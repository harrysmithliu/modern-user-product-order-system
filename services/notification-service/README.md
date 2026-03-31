# notification-service

Lightweight Python worker that consumes RabbitMQ order events and logs notification-style output.

## Responsibility

- consume order lifecycle events from RabbitMQ
- log structured notification records for demo and debugging use
- keep the event pipeline visible before MongoDB-backed audit storage is introduced

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

## Near-Term TODO

- persist consumed events into MongoDB audit collections
- add delivery retry metrics and dead-letter queue support
