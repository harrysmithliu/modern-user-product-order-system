# Architecture

## Core Transaction Path

- `gateway` receives external traffic
- `user-service` authenticates users and manages user profile data
- `product-service` serves catalog queries and performs inventory reservation/release for internal order flows
- `order-service` owns order creation and state transitions

## Data Responsibilities

- MySQL:
  - source of truth for users, products, orders
- Redis:
  - active for product catalog query cache in `product-service`
  - active for token blacklist support shared by `user-service` and `gateway`
  - active for gateway login and order creation rate limiting
- RabbitMQ:
  - active for order lifecycle event fan-out from `order-service`
  - consumed by `notification-service` for structured notification logging
- MongoDB:
  - active as an optional `order_event_timeline` sink in `notification-service`
  - still reserved for side-channel audit logs and notification records outside the critical write path

## MongoDB Reservation Strategy

MongoDB is intentionally reserved for side-channel operational data instead of core transaction records.

Planned usage:

- order event timeline documents
- notification delivery records
- gateway or admin audit logs

Not planned for the critical write path:

- user source-of-truth records
- product source-of-truth records
- order source-of-truth records
- inventory mutation authority

## Service Interaction

1. User logs in through `gateway`
2. `gateway` proxies login to `user-service`
3. `user-service` validates BCrypt password and issues JWT
4. Product queries are served by `product-service`
5. Order creation is handled by `order-service`
6. `order-service` calls internal `product-service` endpoints to reserve or release inventory
7. `order-service` emits lifecycle events to RabbitMQ after transaction commit
8. `notification-service` consumes the events, records notification-style logs, and can persist them into MongoDB for audit lookup

## Future Release Flow

```mermaid
flowchart LR
    A["Developer branch / local change"] --> B["Dev environment\nlocal process or docker-compose.dev.yml"]
    B --> C["Push to GitHub"]
    C --> D["CI build and test\nfuture GitHub Actions pipeline"]
    D --> E["Sandbox deployment\ndocker-compose.sandbox.yml or future sandbox K8s"]
    E --> F["Manual review, smoke test, demo validation"]
    F --> G["Prod release approval"]
    G --> H["Prod deployment\nfuture EKS / Kubernetes rollout"]
```

## Branch-to-Environment Mapping

- `dev` branch
  - feeds the `dev` runtime environment
  - optimized for ongoing implementation work
- `sandbox` branch
  - feeds the `sandbox` runtime environment
  - optimized for integrated verification and demo readiness
- `main` branch
  - represents the latest stable baseline
  - reserved as the future production promotion source

Recommended promotion chain:

```text
feature/* -> dev -> sandbox -> main
```

## Design Principles

- keep the first version runnable before making it sophisticated
- keep core transaction data in MySQL
- reserve document/event stores for side-channel data
- use shared response envelopes across services
- keep MongoDB out of the critical write path for Phase 1
