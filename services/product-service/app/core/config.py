from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "product-service"
    db_host: str = "localhost"
    db_port: int = 3306
    db_name: str = "h_product_db"
    db_user: str = "app"
    db_password: str = "app_pass"
    sqlalchemy_pool_size: int = 30
    sqlalchemy_max_overflow: int = 60
    sqlalchemy_pool_timeout_seconds: int = 10
    sqlalchemy_pool_recycle_seconds: int = 1800
    jwt_secret: str = "change-me-in-env"
    jwt_algorithm: str = "HS256"
    redis_enabled: bool = True
    redis_host: str = "localhost"
    redis_port: int = 6379
    redis_db: int = 0
    redis_password: str | None = None
    redis_cache_ttl_seconds: int = 120
    internal_api_token: str = "change-me-internal-token"
    coupon_rate_limit_window_seconds: int = 60
    coupon_issue_rate_limit_max_requests: int = 30
    coupon_claim_rate_limit_max_requests: int = 60
    coupon_order_record_ttl_seconds: int = 2592000
    coupon_issue_fail_sim_enabled: bool = False
    coupon_issue_fail_sim_ratio: float = 0.3

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
