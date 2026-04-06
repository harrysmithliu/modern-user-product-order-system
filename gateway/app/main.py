from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from prometheus_fastapi_instrumentator import Instrumentator

from app.core.config import settings
from app.core.proxy import router


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield


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
