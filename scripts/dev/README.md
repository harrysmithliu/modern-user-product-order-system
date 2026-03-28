# Dev Notes

Recommended startup order for the current Phase 1 batch:

1. `services/user-service`
2. `services/product-service`
3. `services/order-service`
4. `gateway`
5. `frontend`

Suggested smoke checks after startup:

1. `POST /api/auth/login`
2. `GET /api/products?page=1&size=5`
3. `POST /api/orders`
4. `GET /api/orders/my`
5. `GET /api/admin/orders`
