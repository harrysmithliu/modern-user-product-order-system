from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "user-service"
    db_host: str = "localhost"
    db_port: int = 3306
    db_name: str = "h_user_db"
    db_user: str = "app"
    db_password: str = "app_pass"
    jwt_secret: str = "change-me-in-env"
    jwt_algorithm: str = "HS256"
    jwt_expire_minutes: int = 120

    model_config = SettingsConfigDict(
        env_file=".env",
        env_prefix="USER_SERVICE_",
        extra="ignore",
    )

    @property
    def database_url(self) -> str:
        return (
            f"mysql+pymysql://{self.db_user}:{self.db_password}"
            f"@{self.db_host}:{self.db_port}/{self.db_name}"
        )


settings = Settings()
