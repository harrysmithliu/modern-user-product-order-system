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

Automated Phase 1 smoke test:

```bash
python3 scripts/dev/smoke-test-phase1.py
```

The script creates real sample orders in the local development database so the review and history pages have data to display.

The script assumes these services are already running locally:

1. `user-service` on `127.0.0.1:8001`
2. `product-service` on `127.0.0.1:8002`
3. `order-service` on `127.0.0.1:8080`
4. `gateway` on `127.0.0.1:8000`

The smoke test covers:

1. user login
2. admin login
3. product listing
4. create + cancel flow
5. create + approve flow
6. create + reject flow
7. latest-first order sorting
8. pending queue verification
