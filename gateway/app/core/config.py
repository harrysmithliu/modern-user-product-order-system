from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    jwt_secret: str = "change-me-in-env"
    jwt_algorithm: str = "HS256"
    cors_origins: list[str] = ["http://localhost:5173"]
    user_service_url: str = "http://localhost:8001"
    product_service_url: str = "http://localhost:8002"
    order_service_url: str = "http://localhost:8080"
    request_timeout_seconds: int = 15

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="GATEWAY_",
        extra="ignore",
    )


settings = Settings()
