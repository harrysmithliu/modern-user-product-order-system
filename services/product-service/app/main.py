from fastapi import FastAPI
from prometheus_fastapi_instrumentator import Instrumentator

from app.api.routes import router

app = FastAPI(
    title="product-service",
    version="0.1.0",
)

app.include_router(router)

Instrumentator(
    excluded_handlers=["/health", "/ready", "/live", "/metrics"],
).instrument(app).expose(app, include_in_schema=False)
