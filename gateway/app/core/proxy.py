import re
from typing import Optional

import httpx
from fastapi import APIRouter, HTTPException, Request, Response, status

from app.core.cache import increment_rate_limit
from app.core.config import settings
from app.core.security import CurrentUser, get_current_user, get_optional_current_user

router = APIRouter()
_http_client: httpx.AsyncClient | None = None


def set_http_client(client: httpx.AsyncClient) -> None:
    global _http_client
    _http_client = client


def clear_http_client() -> None:
    global _http_client
    _http_client = None


def get_http_client() -> httpx.AsyncClient:
    if _http_client is None:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail="Gateway upstream client not ready")
    return _http_client


def resolve_target(path: str) -> str:
    if path.startswith("/api/auth"):
        return f"{settings.user_service_url}{path.replace('/api', '', 1)}"
    if path.startswith("/api/users") or path.startswith("/api/admin/users"):
        return f"{settings.user_service_url}{path.replace('/api', '', 1)}"
    if path.startswith("/api/products") or path.startswith("/api/admin/products"):
        return f"{settings.product_service_url}{path.replace('/api', '', 1)}"
    if path.startswith("/api/orders") or path.startswith("/api/admin/orders"):
        return f"{settings.order_service_url}{path.replace('/api', '', 1)}"
    raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Route not found")


def is_public_route(request: Request) -> bool:
    if request.url.path == "/api/auth/login":
        return True
    if request.method == "GET" and request.url.path == "/api/products":
        return True
    if request.method == "GET" and re.fullmatch(r"/api/products/\d+", request.url.path):
        return True
    return request.url.path in {"/health", "/ready", "/live"}


def enforce_role(path: str, current_user: Optional[CurrentUser]) -> None:
    if "/api/admin/" in path:
        if not current_user:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication required")
        if current_user.role != "ADMIN":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin role required")


def apply_rate_limit(request: Request, current_user: Optional[CurrentUser]) -> None:
    if request.method == "POST" and request.url.path == "/api/auth/login":
        client_ip = request.client.host if request.client else "unknown"
        key = f"gateway:ratelimit:login:{client_ip}"
        current = increment_rate_limit(key, settings.login_rate_limit_window_seconds)
        if current is not None and current > settings.login_rate_limit_max_requests:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Too many login attempts. Please try again later.",
            )
        return

    if request.method == "POST" and request.url.path == "/api/orders":
        principal = (
            f"user:{current_user.user_id}"
            if current_user
            else f"ip:{request.client.host if request.client else 'unknown'}"
        )
        key = f"gateway:ratelimit:order-create:{principal}"
        current = increment_rate_limit(key, settings.order_create_rate_limit_window_seconds)
        if current is not None and current > settings.order_create_rate_limit_max_requests:
            raise HTTPException(
                status_code=status.HTTP_429_TOO_MANY_REQUESTS,
                detail="Order submission rate limit exceeded. Please retry shortly.",
            )


async def forward_request(request: Request, current_user: Optional[CurrentUser]) -> Response:
    target_url = resolve_target(request.url.path)
    headers = {k: v for k, v in request.headers.items() if k.lower() != "host"}
    if current_user:
        headers["x-user-id"] = str(current_user.user_id)
        headers["x-username"] = current_user.username
        headers["x-user-role"] = current_user.role

    body = await request.body()
    client = get_http_client()
    upstream = await client.request(
        request.method,
        target_url,
        content=body,
        params=request.query_params,
        headers=headers,
    )

    excluded_headers = {"content-encoding", "transfer-encoding", "connection"}
    response_headers = {
        key: value for key, value in upstream.headers.items() if key.lower() not in excluded_headers
    }
    return Response(
        content=upstream.content,
        status_code=upstream.status_code,
        headers=response_headers,
        media_type=upstream.headers.get("content-type"),
    )


@router.api_route(
    "/api/{path:path}",
    methods=["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
)
async def gateway_proxy(path: str, request: Request):
    current_user = (
        get_optional_current_user(request.headers.get("authorization"))
        if is_public_route(request)
        else get_current_user(request.headers.get("authorization"))
    )
    enforce_role(request.url.path, current_user)
    apply_rate_limit(request, current_user)
    return await forward_request(request, current_user)
