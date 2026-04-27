from contextlib import asynccontextmanager

import httpx
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from prometheus_fastapi_instrumentator import Instrumentator

from app.core.config import settings
from app.core.proxy import clear_http_client, router, set_http_client


@asynccontextmanager
async def lifespan(_: FastAPI):
    timeout = httpx.Timeout(settings.request_timeout_seconds)
    limits = httpx.Limits(
        max_connections=settings.upstream_max_connections,
        max_keepalive_connections=settings.upstream_max_keepalive_connections,
        keepalive_expiry=settings.upstream_keepalive_expiry_seconds,
    )
    client = httpx.AsyncClient(timeout=timeout, limits=limits)
    set_http_client(client)
    yield
    await client.aclose()
    clear_http_client()


app = FastAPI(
    title="api-gateway",
    version="0.1.0",
    lifespan=lifespan,
    docs_url=None,
    redoc_url=None,
    openapi_url=None,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)

Instrumentator(
    excluded_handlers=["/health", "/ready", "/live", "/metrics"],
).instrument(app).expose(app, include_in_schema=False)


@app.get("/health", include_in_schema=False)
async def health():
    return {
        "status": "UP",
        "service": "gateway",
    }


@app.get("/ready", include_in_schema=False)
async def ready():
    return {
        "status": "READY",
        "service": "gateway",
    }


@app.get("/live", include_in_schema=False)
async def live():
    return {
        "status": "LIVE",
        "service": "gateway",
    }
