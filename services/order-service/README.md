# order-service

Spring Boot service responsible for order creation and approval flow.

## Current Endpoints

- `POST /orders`
- `POST /orders/{id}/cancel`
- `GET /orders/my`
- `GET /admin/orders`
- `POST /admin/orders/{id}/approve`
- `POST /admin/orders/{id}/reject`

## Local Run

```bash
mvn spring-boot:run
```

## Dependencies

- MySQL `h_order_db`
- product-service internal APIs on `http://localhost:8002`
