# Modern User-Product-Order System

A polyglot microservices demo for portfolio use, centered around `user`, `product`, and `order` domains.

## Current Scope

This repository is being built in phases.

- Phase 1: core business loop
  - login
  - user profile management
  - product pagination
  - create order
  - cancel order
  - admin order approval/rejection
- Phase 2: Redis, RabbitMQ, Docker Compose, unified production polish
- Phase 3: Kubernetes, monitoring, load testing, AWS migration notes

## Services

- `gateway`: FastAPI API gateway, JWT verification, routing, CORS
- `services/user-service`: FastAPI user service
- `services/product-service`: FastAPI product service
- `services/order-service`: Spring Boot order service
- `frontend`: React + Vite skeleton

## Infrastructure Notes

- MySQL is reused from the local environment with:
  - `h_user_db`
  - `h_product_db`
  - `h_order_db`
- Redis and RabbitMQ are already available locally
- MongoDB is intentionally reserved for a later audit/event timeline extension, not the core transaction path

## Docs

- [Architecture](./docs/architecture.md)
- [Database Design](./docs/database-design.md)
- [API Overview](./docs/api-overview.md)

## Quick Start

Each service currently has its own local configuration defaults. Environment-specific values can be overridden with environment variables.

## Local Run Commands

### Gateway

```bash
cd gateway
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

### User Service

```bash
cd services/user-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

### Product Service

```bash
cd services/product-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8002
```

### Order Service

```bash
cd services/order-service
mvn spring-boot:run
```

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## Phase 1 Notes

- `user-service` is already wired to the local BCrypt user data you prepared
- `product-service` includes public catalog APIs and internal stock reservation APIs
- `order-service` includes create, cancel, my orders, admin list, approve, and reject
- `gateway` routes `/api/**` and injects request user headers for downstream services
- MongoDB is only reserved in docs and architecture for later audit/event expansion
