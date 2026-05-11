# gateway-go

Go translation of the existing Python FastAPI gateway.

## Goal

The first implementation target is behavior parity with `gateway/`, not a redesign.

The Go gateway preserves:

- health endpoints: `/health`, `/ready`, `/live`
- Prometheus metrics endpoint: `/metrics`
- CORS behavior for the frontend
- JWT verification for protected routes
- optional JWT parsing for public routes
- admin access enforcement for `/api/admin/**`
- Redis-backed token blacklist checks
- Redis-backed fixed-window rate limiting
- route forwarding to user-service, product-service, and order-service
- downstream user context headers:
  - `x-user-id`
  - `x-username`
  - `x-user-role`

## Route Mapping

- `/api/auth/**` -> `user-service`
- `/api/users/**` -> `user-service`
- `/api/admin/users/**` -> `user-service`
- `/api/products/**` -> `product-service`
- `/api/admin/products/**` -> `product-service`
- `/api/orders/**` -> `order-service`
- `/api/admin/orders/**` -> `order-service`

The gateway strips the leading `/api` prefix before forwarding downstream.

## Public vs Protected Routes

Public:

- `POST /api/auth/login`
- `GET /api/products`
- `GET /api/products/{id}`

Protected:

- everything else under `/api/**`
- `/api/admin/**` requires `role=ADMIN`

Public routes accept an optional bearer token. If present and valid, the gateway forwards user context headers to downstream services.

## Directory Guide

```text
gateway-go/
  cmd/gateway/main.go
  internal/cache       Redis blacklist and fixed-window rate limiting
  internal/config      GATEWAY_* environment-backed config
  internal/middleware  CORS and Prometheus HTTP metrics
  internal/proxy       route resolution, auth policy, rate limits, forwarding
  internal/security    JWT parsing and CurrentUser extraction
```

## Local Run

```bash
go run ./cmd/gateway
```

By default, the service listens on `:8000`.

Health checks:

```bash
curl -i http://127.0.0.1:8000/health
curl -i http://127.0.0.1:8000/ready
curl -i http://127.0.0.1:8000/live
```

Metrics:

```bash
curl -i http://127.0.0.1:8000/metrics
```

Public product proxy example:

```bash
curl -i "http://127.0.0.1:8000/api/products?page=1&size=10"
```

## Environment Variables

See `.env.example`.

The variable names intentionally follow the Python gateway `GATEWAY_` prefix so local, Docker, and Kubernetes configuration can stay aligned.

Important values:

- `GATEWAY_HTTP_ADDR`
- `GATEWAY_JWT_SECRET`
- `GATEWAY_USER_SERVICE_URL`
- `GATEWAY_PRODUCT_SERVICE_URL`
- `GATEWAY_ORDER_SERVICE_URL`
- `GATEWAY_REDIS_HOST`
- `GATEWAY_REDIS_PORT`
- `GATEWAY_LOGIN_RATE_LIMIT_MAX_REQUESTS`
- `GATEWAY_ORDER_CREATE_RATE_LIMIT_MAX_REQUESTS`

## Behavior Parity Notes

- Redis errors degrade open for token blacklist checks and rate limiting, matching the Python gateway.
- Token blacklist key format is shared with `user-service`: `user-service:blacklist:{sha256(token)}`.
- Login rate limit key: `gateway:ratelimit:login:{client_ip}`.
- Order-create rate limit key: `gateway:ratelimit:order-create:user:{user_id}`.
- Upstream timeout returns `504`.
- Upstream request/connect error returns `502`.
- Upstream response status/body/content-type are forwarded as-is.

## Tests

```bash
GOCACHE=/tmp/gocache-gateway-go go test ./...
```

`GOCACHE` is optional in normal local development. It is useful in restricted environments where the default Go build cache path is not writable.

## Current Boundary

This Go gateway is ready for local behavior validation. Dockerfile and compose/k8s service wiring are intentionally left for a follow-up step so the first port stays focused on code-level parity.
