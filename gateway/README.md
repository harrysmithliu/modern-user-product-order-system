# gateway

FastAPI-based API gateway for the Modern User-Product-Order System.

## Responsibility

- expose a single external API entry point
- proxy requests to downstream services
- verify JWT for protected routes
- enforce admin access on `/api/admin/**`
- attach request user context headers for downstream services
- handle CORS for the frontend

This gateway is intentionally lightweight for Phase 1. It is not yet a full production gateway with rate limiting, tracing, and centralized exception mapping.

## Route Mapping

- `/api/auth/**` -> `user-service`
- `/api/users/**` -> `user-service`
- `/api/products/**` -> `product-service`
- `/api/admin/products/**` -> `product-service`
- `/api/orders/**` -> `order-service`
- `/api/admin/orders/**` -> `order-service`

## Directory Guide

- `app/main.py`: FastAPI app entry
- `app/core/config.py`: environment-backed settings
- `app/core/security.py`: JWT decode helpers
- `app/core/proxy.py`: route resolution and upstream forwarding

## Local Run

```bash
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

## Environment Variables

See:

- [gateway/.env.example](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/gateway/.env.example)

Important values:

- `GATEWAY_JWT_SECRET`
- `GATEWAY_USER_SERVICE_URL`
- `GATEWAY_PRODUCT_SERVICE_URL`
- `GATEWAY_ORDER_SERVICE_URL`

## Public vs Protected Routes

Public:

- `POST /api/auth/login`
- `GET /api/products`
- `GET /api/products/{id}`

Protected:

- everything under `/api/users/**`
- everything under `/api/orders/**`
- everything under `/api/admin/**`

## Maintenance Notes

- Public product APIs still accept an optional bearer token so admin users can request `include_off_sale=true`.
- Downstream user context is passed via:
  - `X-User-Id`
  - `X-Username`
  - `X-User-Role`
- The gateway currently forwards response bodies as-is from upstream services.

## Near-Term TODO

- add unified error envelope handling in the gateway layer
- add request logging with trace IDs
- add rate limiting
- add refresh token / blacklist support through Redis
- add service discovery-friendly configuration for Docker Compose and Kubernetes
