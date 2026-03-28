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
  - reserved for product list cache, token support data, and rate limiting
- RabbitMQ:
  - reserved for order event fan-out in Phase 2
- MongoDB:
  - reserved for audit logs, order event timeline, and notification records

## Service Interaction

1. User logs in through `gateway`
2. `gateway` proxies login to `user-service`
3. `user-service` validates BCrypt password and issues JWT
4. Product queries are served by `product-service`
5. Order creation is handled by `order-service`
6. `order-service` calls internal `product-service` endpoints to reserve or release inventory

## Design Principles

- keep the first version runnable before making it sophisticated
- keep core transaction data in MySQL
- reserve document/event stores for side-channel data
- use shared response envelopes across services
- keep MongoDB out of the critical write path for Phase 1
