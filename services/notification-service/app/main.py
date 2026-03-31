from __future__ import annotations

import json
import logging
import sys
import time
from pathlib import Path

import pika
from pika.exceptions import AMQPConnectionError

if __package__ in (None, ""):
    sys.path.append(str(Path(__file__).resolve().parents[1]))

from app.config import settings


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s %(message)s",
)
logger = logging.getLogger("notification-service")


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
        "occurred_at": value("occurred_at", "occurredAt"),
    }


def on_message(channel: pika.adapters.blocking_connection.BlockingChannel, method, _properties, body: bytes) -> None:
    payload = json.loads(body.decode("utf-8"))
    log_record = build_log_record(payload, method.routing_key)
    logger.info("notification_event=%s", json.dumps(log_record, sort_keys=True))
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
