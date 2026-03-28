from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.core.config import settings
from app.core.proxy import router


@asynccontextmanager
async def lifespan(_: FastAPI):
    yield


app = FastAPI(
    title="api-gateway",
    version="0.1.0",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(router)


@app.get("/health")
async def health():
    return {
        "status": "UP",
        "service": "gateway",
    }


@app.get("/ready")
async def ready():
    return {
        "status": "READY",
        "service": "gateway",
    }


@app.get("/live")
async def live():
    return {
        "status": "LIVE",
        "service": "gateway",
    }
