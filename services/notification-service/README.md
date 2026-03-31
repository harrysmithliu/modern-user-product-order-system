# notification-service

Lightweight Python worker that consumes RabbitMQ order events, logs notification-style output, and can persist an audit timeline into MongoDB.

## Responsibility

- consume order lifecycle events from RabbitMQ
- log structured notification records for demo and debugging use
- persist consumed order events into MongoDB when audit storage is enabled

## Current Event Bindings

- `order.created`
- `order.cancelled`
- `order.approved`
- `order.rejected`

## Local Run

```bash
python -m app.main
```

## Environment Variables

See:

- `services/notification-service/.env.example`

## Docker

- Dockerfile: `services/notification-service/Dockerfile`
- Compose service name: `notification-service`
- In the `dev` runtime, this worker is expected to connect to your host-managed local broker, such as `rmq` on `localhost:5672`.

## Dependencies

- RabbitMQ
- `order.events` topic exchange
- optional MongoDB audit store

## Audit Storage

When `NOTIFICATION_SERVICE_MONGO_ENABLED=true`, the worker stores consumed order events into the configured MongoDB collection.

Relevant settings:

- `NOTIFICATION_SERVICE_MONGO_URI`
- `NOTIFICATION_SERVICE_MONGO_DATABASE`
- `NOTIFICATION_SERVICE_MONGO_COLLECTION`

The default collection name is `order_event_timeline`.

## Near-Term TODO

- add delivery retry metrics and dead-letter queue support
