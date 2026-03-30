# gateway

FastAPI-based API gateway for the Modern User-Product-Order System.

## Responsibility

- expose a single external API entry point
- proxy requests to downstream services
- verify JWT for protected routes
- enforce admin access on `/api/admin/**`
- attach request user context headers for downstream services
- handle CORS for the frontend
- apply Redis-backed fixed-window rate limiting for high-risk entry points
- reject access tokens that have been blacklisted by `user-service`

This gateway is intentionally lightweight, but it now includes basic Redis-backed rate limiting and token blacklist checks for Phase 2 hardening.

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

## API Docs

- Gateway OpenAPI docs are intentionally disabled.
- Health endpoints remain available:
  - `http://localhost:8000/health`
  - `http://localhost:8000/ready`
  - `http://localhost:8000/live`

## Environment Variables

See:

- `gateway/.env.example`

Important values:

- `GATEWAY_JWT_SECRET`
- `GATEWAY_USER_SERVICE_URL`
- `GATEWAY_PRODUCT_SERVICE_URL`
- `GATEWAY_ORDER_SERVICE_URL`
- `GATEWAY_REDIS_HOST`
- `GATEWAY_REDIS_PORT`
- `GATEWAY_LOGIN_RATE_LIMIT_MAX_REQUESTS`
- `GATEWAY_ORDER_CREATE_RATE_LIMIT_MAX_REQUESTS`

## Docker

- Dockerfile: `gateway/Dockerfile`
- Compose service name: `gateway`
- Local container health: `http://localhost:8000/health`

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
- Login requests and order creation requests now use fixed-window Redis counters.
- Blacklisted tokens are denied at the gateway before downstream proxying.

## Near-Term TODO

- add unified error envelope handling in the gateway layer
- add request logging with trace IDs
- refine rate limit policies per route and environment
- add refresh token flow on top of the existing Redis-backed blacklist support
- add service discovery-friendly configuration for Docker Compose and Kubernetes
