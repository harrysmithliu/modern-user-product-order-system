from __future__ import annotations

import json
import logging
import sys
import time
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import pika
from pika.exceptions import AMQPConnectionError

try:
    from pymongo import MongoClient
except ImportError:  # pragma: no cover
    MongoClient = None

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.config import settings


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("notification-service")
_mongo_client = None
_audit_collection = None


def normalize_timestamp(value: Any) -> Any:
    if isinstance(value, (int, float)):
        return datetime.fromtimestamp(value, timezone.utc).isoformat()
    return value


def build_connection_parameters() -> pika.ConnectionParameters:
    credentials = pika.PlainCredentials(
        settings.rabbitmq_username,
        settings.rabbitmq_password,
    )
    return pika.ConnectionParameters(
        host=settings.rabbitmq_host,
        port=settings.rabbitmq_port,
        virtual_host=settings.rabbitmq_virtual_host,
        credentials=credentials,
        heartbeat=30,
    )


def build_log_record(payload: dict, routing_key: str) -> dict:
    def value(*keys: str):
        for key in keys:
            if key in payload:
                return payload[key]
        return None

    return {
        "consumer": "notification-service",
        "routing_key": routing_key,
        "event_type": value("event_type", "eventType"),
        "order_no": value("order_no", "orderNo"),
        "status": value("status"),
        "user_id": value("user_id", "userId"),
        "operator_username": value("operator_username", "operatorUsername"),
        "occurred_at": normalize_timestamp(value("occurred_at", "occurredAt")),
    }


def build_audit_record(payload: dict[str, Any], routing_key: str, consumed_at: str | None = None) -> dict[str, Any]:
    def value(*keys: str):
        for key in keys:
            if key in payload:
                return payload[key]
        return None

    message_id = value("message_id", "messageId")
    if not message_id:
        message_id = f"{routing_key}:{value('order_no', 'orderNo')}:{value('occurred_at', 'occurredAt')}"

    return {
        "message_id": message_id,
        "consumer": "notification-service",
        "routing_key": routing_key,
        "event_type": value("event_type", "eventType"),
        "order_id": value("order_id", "orderId"),
        "order_no": value("order_no", "orderNo"),
        "request_no": value("request_no", "requestNo"),
        "user_id": value("user_id", "userId"),
        "product_id": value("product_id", "productId"),
        "quantity": value("quantity"),
        "total_amount": value("total_amount", "totalAmount"),
        "status_code": value("status_code", "statusCode"),
        "status": value("status"),
        "operator_id": value("operator_id", "operatorId"),
        "operator_username": value("operator_username", "operatorUsername"),
        "operator_role": value("operator_role", "operatorRole"),
        "reason": value("reason"),
        "occurred_at": normalize_timestamp(value("occurred_at", "occurredAt")),
        "consumed_at": consumed_at or datetime.now(timezone.utc).isoformat(),
        "payload": payload,
    }


def get_audit_collection():
    global _mongo_client, _audit_collection
    if not settings.mongo_enabled:
        return None
    if MongoClient is None:
        logger.warning("MongoDB audit storage enabled but pymongo is not installed")
        return None
    if _audit_collection is None:
        _mongo_client = MongoClient(
            settings.mongo_uri,
            serverSelectionTimeoutMS=settings.mongo_timeout_ms,
        )
        _audit_collection = _mongo_client[settings.mongo_database][settings.mongo_collection]
        _audit_collection.create_index("message_id", unique=True)
    return _audit_collection


def persist_audit_record(collection, document: dict[str, Any]) -> bool:
    if collection is None:
        return False
    collection.update_one(
        {"message_id": document["message_id"]},
        {"$setOnInsert": document},
        upsert=True,
    )
    return True


def on_message(channel: pika.adapters.blocking_connection.BlockingChannel, method, _properties, body: bytes) -> None:
    payload = json.loads(body.decode("utf-8"))
    log_record = build_log_record(payload, method.routing_key)
    audit_record = build_audit_record(payload, method.routing_key)
    logger.info("notification_event=%s", json.dumps(log_record, sort_keys=True))
    try:
        persisted = persist_audit_record(get_audit_collection(), audit_record)
        if persisted:
            logger.info(
                "notification_audit_saved=%s",
                json.dumps(
                    {
                        "message_id": audit_record["message_id"],
                        "database": settings.mongo_database,
                        "collection": settings.mongo_collection,
                    },
                    sort_keys=True,
                ),
            )
    except Exception:
        logger.exception("failed to persist notification audit record to MongoDB")
    channel.basic_ack(delivery_tag=method.delivery_tag)


def consume_forever() -> None:
    parameters = build_connection_parameters()
    while True:
        try:
            connection = pika.BlockingConnection(parameters)
            channel = connection.channel()
            channel.exchange_declare(
                exchange=settings.exchange,
                exchange_type="topic",
                durable=True,
            )
            channel.queue_declare(queue=settings.queue_name, durable=True)
            for routing_key in settings.routing_keys:
                channel.queue_bind(
                    exchange=settings.exchange,
                    queue=settings.queue_name,
                    routing_key=routing_key,
                )

            logger.info(
                "notification consumer ready exchange=%s queue=%s bindings=%s",
                settings.exchange,
                settings.queue_name,
                ",".join(settings.routing_keys),
            )
            channel.basic_qos(prefetch_count=10)
            channel.basic_consume(queue=settings.queue_name, on_message_callback=on_message)
            channel.start_consuming()
        except AMQPConnectionError:
            logger.warning(
                "RabbitMQ unavailable, retrying in %s seconds",
                settings.reconnect_seconds,
            )
            time.sleep(settings.reconnect_seconds)
        except KeyboardInterrupt:
            logger.info("notification consumer stopped")
            return


if __name__ == "__main__":
    consume_forever()
