from __future__ import annotations

import os


class Settings:
    rabbitmq_host: str = os.getenv("NOTIFICATION_SERVICE_RABBITMQ_HOST", "localhost")
    rabbitmq_port: int = int(os.getenv("NOTIFICATION_SERVICE_RABBITMQ_PORT", "5672"))
    rabbitmq_username: str = os.getenv("NOTIFICATION_SERVICE_RABBITMQ_USERNAME", "admin")
    rabbitmq_password: str = os.getenv("NOTIFICATION_SERVICE_RABBITMQ_PASSWORD", "admin123")
    rabbitmq_virtual_host: str = os.getenv("NOTIFICATION_SERVICE_RABBITMQ_VIRTUAL_HOST", "/")
    exchange: str = os.getenv("NOTIFICATION_SERVICE_EXCHANGE", "order.events")
    queue_name: str = os.getenv("NOTIFICATION_SERVICE_QUEUE_NAME", "notification.order.events")
    reconnect_seconds: int = int(os.getenv("NOTIFICATION_SERVICE_RECONNECT_SECONDS", "5"))
    mongo_enabled: bool = os.getenv("NOTIFICATION_SERVICE_MONGO_ENABLED", "false").lower() == "true"
    mongo_uri: str = os.getenv(
        "NOTIFICATION_SERVICE_MONGO_URI",
        "mongodb://admin:admin123@localhost:27017/admin?authSource=admin",
    )
    mongo_database: str = os.getenv("NOTIFICATION_SERVICE_MONGO_DATABASE", "upo_audit")
    mongo_collection: str = os.getenv("NOTIFICATION_SERVICE_MONGO_COLLECTION", "order_event_timeline")
    mongo_timeout_ms: int = int(os.getenv("NOTIFICATION_SERVICE_MONGO_TIMEOUT_MS", "3000"))
    routing_keys: list[str] = [
        key.strip()
        for key in os.getenv(
            "NOTIFICATION_SERVICE_ROUTING_KEYS",
            "order.created,order.cancelled,order.approved,order.rejected",
        ).split(",")
        if key.strip()
    ]


settings = Settings()
