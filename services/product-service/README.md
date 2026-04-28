# product-service

FastAPI service responsible for catalog queries and product administration.

## Responsibility

- public product pagination
- public product detail query
- admin product creation and update
- admin product status toggle
- admin stock update
- internal stock reserve/release APIs for order flow

## Database

- schema: `h_product_db`
- table: `t_product`

Required fields currently used by the service:

- `id`
- `product_name`
- `product_code`
- `price`
- `stock`
- `category`
- `status`
- `version`

## Important Environment Requirement

The MySQL application user must have access to `h_product_db`.

Example grant:

```sql
GRANT ALL PRIVILEGES ON h_product_db.* TO 'app'@'%';
FLUSH PRIVILEGES;
```

Without that grant, the service will fail at query time with MySQL access denied errors.

## Directory Guide

- `app/main.py`: service entry
- `app/core/cache.py`: Redis cache helpers and catalog version invalidation
- `app/api/routes.py`: public, admin, and internal APIs
- `app/core/security.py`: admin check helper
- `app/db/session.py`: SQLAlchemy session setup
- `app/models/product.py`: ORM model
- `app/services/product_service.py`: pagination and stock mutation logic

## Local Run

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8002
```

## API Docs

- Swagger UI: `http://localhost:8002/docs`
- Health probes are intentionally hidden from the generated schema:
  - `http://localhost:8002/health`
  - `http://localhost:8002/ready`
  - `http://localhost:8002/live`

## Environment Variables

See:

- `services/product-service/.env.example`

Important Redis-related variables:

- `PRODUCT_SERVICE_REDIS_ENABLED`
- `PRODUCT_SERVICE_REDIS_HOST`
- `PRODUCT_SERVICE_REDIS_PORT`
- `PRODUCT_SERVICE_REDIS_DB`
- `PRODUCT_SERVICE_REDIS_PASSWORD`
- `PRODUCT_SERVICE_REDIS_CACHE_TTL_SECONDS`

Connection pool variables:

- `PRODUCT_SERVICE_SQLALCHEMY_POOL_SIZE`
- `PRODUCT_SERVICE_SQLALCHEMY_MAX_OVERFLOW`
- `PRODUCT_SERVICE_SQLALCHEMY_POOL_TIMEOUT_SECONDS`
- `PRODUCT_SERVICE_SQLALCHEMY_POOL_RECYCLE_SECONDS`

Coupon/internal variables:

- `PRODUCT_SERVICE_INTERNAL_API_TOKEN`
- `PRODUCT_SERVICE_COUPON_RATE_LIMIT_WINDOW_SECONDS`
- `PRODUCT_SERVICE_COUPON_ISSUE_RATE_LIMIT_MAX_REQUESTS`
- `PRODUCT_SERVICE_COUPON_CLAIM_RATE_LIMIT_MAX_REQUESTS`

## Docker

- Dockerfile: `services/product-service/Dockerfile`
- Compose service name: `product-service`
- Local container docs: `http://localhost:8002/docs`

## Current Endpoints

Public:

- `GET /products`
- `GET /products/{product_id}`

Admin:

- `POST /admin/products`
- `PUT /admin/products/{product_id}`
- `PUT /admin/products/{product_id}/status`
- `PUT /admin/products/{product_id}/stock`

Internal:

- `GET /internal/products/{product_id}`
- `POST /internal/products/{product_id}/reserve`
- `POST /internal/products/{product_id}/release`
- `POST /internal/products/{userId}/coupons/issue`
- `POST /internal/products/{userId}/coupons/claim-best`

## Maintenance Notes

- `include_off_sale=true` is restricted to admin callers.
- Stock reserve and release are written as direct SQL updates for Phase 1 simplicity.
- Inventory still lives in `t_product`; a dedicated inventory table can be introduced later if the domain grows.
- Redis now caches public product list queries, public product detail queries, and internal product detail lookups.
- Product creation, updates, stock changes, and reserve/release operations bump a shared catalog cache version so stale keys fall out naturally.
- Coupon issue/claim-best is stored in Redis hash per user and `claim-best` uses a Lua script for atomic best-coupon deduction.
- Coupon internal endpoints require `X-Internal-Token` header and include Redis-backed rate limiting.

## Near-Term TODO

- add product validation and business exceptions with clearer error codes
- add optimistic-lock-aware stock updates using explicit `version` checks
- add cache metrics and observability around hit / miss rates
- add category management if the admin domain expands
