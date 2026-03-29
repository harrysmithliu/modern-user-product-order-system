# Database Design

## User DB

Table: `h_user_db.t_user`

- existing local table is reused
- `role` is used for authorization with values `USER` and `ADMIN`
- passwords are stored as BCrypt hashes

## Product DB

Table: `h_product_db.t_product`

- existing local table is reused
- `status` is used as `1=ON_SALE`, `0=OFF_SALE`
- `version` is reserved for optimistic concurrency handling

## Order DB

Table: `h_order_db.t_order`

- existing local table is reused
- `status` is interpreted as:
  - `0=PENDING_APPROVAL`
  - `1=APPROVED`
  - `2=REJECTED`
  - `3=CANCELLED`
- `request_no` is the idempotency key for order creation
- `uk_user_request_no (user_id, request_no)` prevents duplicate order creation for the same request

Table: `h_order_db.t_message_consume_log`

- reserved for RabbitMQ deduplication in Phase 2

## MongoDB Extension (Reserved)

MongoDB is reserved for document-style operational data rather than transactional source-of-truth records.

Planned collections:

- `order_event_timeline`
- `notification_records`
- `gateway_audit_logs`

Planned purpose:

- append-only order status transition history
- notification send/retry tracking
- request-level audit and troubleshooting data
