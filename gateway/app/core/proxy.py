from typing import Optional

import httpx
from fastapi import APIRouter, HTTPException, Request, Response, status

from app.core.config import settings
from app.core.security import CurrentUser, get_current_user, get_optional_current_user

router = APIRouter()


def resolve_target(path: str) -> str:
    if path.startswith("/api/auth"):
        return f"{settings.user_service_url}{path.replace('/api', '', 1)}"
    if path.startswith("/api/users"):
        return f"{settings.user_service_url}{path.replace('/api', '', 1)}"
    if path.startswith("/api/products") or path.startswith("/api/admin/products"):
        return f"{settings.product_service_url}{path.replace('/api', '', 1)}"
    if path.startswith("/api/orders") or path.startswith("/api/admin/orders"):
        return f"{settings.order_service_url}{path.replace('/api', '', 1)}"
    raise HTTPException(status_code=status.HTTP_404_NOT_FOUND, detail="Route not found")


def is_public_route(request: Request) -> bool:
    if request.url.path == "/api/auth/login":
        return True
    if request.method == "GET" and request.url.path.startswith("/api/products"):
        return True
    return request.url.path in {"/health", "/ready", "/live"}


def enforce_role(path: str, current_user: Optional[CurrentUser]) -> None:
    if "/api/admin/" in path:
        if not current_user:
            raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Authentication required")
        if current_user.role != "ADMIN":
            raise HTTPException(status_code=status.HTTP_403_FORBIDDEN, detail="Admin role required")


async def forward_request(request: Request, current_user: Optional[CurrentUser]) -> Response:
    target_url = resolve_target(request.url.path)
    headers = {k: v for k, v in request.headers.items() if k.lower() != "host"}
    if current_user:
        headers["x-user-id"] = str(current_user.user_id)
        headers["x-username"] = current_user.username
        headers["x-user-role"] = current_user.role

    body = await request.body()
    timeout = httpx.Timeout(settings.request_timeout_seconds)

    async with httpx.AsyncClient(timeout=timeout) as client:
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
    return await forward_request(request, current_user)
