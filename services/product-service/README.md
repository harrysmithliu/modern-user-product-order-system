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

## Environment Variables

See:

- `services/product-service/.env.example`

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

## Maintenance Notes

- `include_off_sale=true` is restricted to admin callers.
- Stock reserve and release are written as direct SQL updates for Phase 1 simplicity.
- Inventory still lives in `t_product`; a dedicated inventory table can be introduced later if the domain grows.

## Near-Term TODO

- add product validation and business exceptions with clearer error codes
- add optimistic-lock-aware stock updates using explicit `version` checks
- add Redis cache for product listing
- add category management if the admin domain expands
