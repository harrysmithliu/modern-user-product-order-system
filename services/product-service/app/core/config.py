from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "product-service"
    db_host: str = "localhost"
    db_port: int = 3306
    db_name: str = "h_product_db"
    db_user: str = "app"
    db_password: str = "app_pass"
    jwt_secret: str = "change-me-in-env"
    jwt_algorithm: str = "HS256"
    redis_enabled: bool = True
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0
    redis_password: str | None = None
    redis_cache_ttl_seconds: int = 120

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="PRODUCT_SERVICE_",
        extra="ignore",
    )

    @property
    def database_url(self) -> str:
        return (
            f"mysql+pymysql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )


settings = Settings()
