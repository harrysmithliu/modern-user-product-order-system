from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    jwt_secret: str = "change-me-in-env"
    jwt_algorithm: str = "HS256"
    cors_origins: list[str] = ["http://localhost:5173", "http://127.0.0.1:5173"]
    user_service_url: str = "http://localhost:8001"
    product_service_url: str = "http://localhost:8002"
    order_service_url: str = "http://localhost:8080"
    request_timeout_seconds: int = 15
    upstream_max_connections: int = 2048
    upstream_max_keepalive_connections: int = 512
    upstream_keepalive_expiry_seconds: int = 30
    redis_enabled: bool = True
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0
    redis_password: str | None = None
    login_rate_limit_max_requests: int = 10
    login_rate_limit_window_seconds: int = 60
    order_create_rate_limit_max_requests: int = 20
    order_create_rate_limit_window_seconds: int = 60

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="GATEWAY_",
        extra="ignore",
    )


settings = Settings()
