# Modern User-Product-Order System

A portfolio-focused polyglot microservices commerce demo built around three core domains:

- users
- products
- orders

The project is intentionally designed as a **minimal but complete modern architecture demo**. It emphasizes clear service boundaries, runnable local development, API-driven frontend integration, and room for production-oriented upgrades such as Redis, RabbitMQ, Kubernetes, and cloud deployment.

## Highlights

- Polyglot backend:
  - `user-service` in Python / FastAPI
  - `product-service` in Python / FastAPI
  - `order-service` in Java / Spring Boot
- Dedicated API gateway with JWT verification and route forwarding
- React + Vite + TypeScript frontend
- MySQL split by domain schema
- Idempotency-ready order design with `request_no`
- Admin and user flows in a single demo UI
- Local-first development with infrastructure already available for Redis and RabbitMQ

## Current Status

The repository is under phased implementation.

- Phase 1:
  - login
  - profile update
  - password change
  - product listing
  - create order
  - cancel order
  - admin order review
  - admin product management
- Phase 2:
  - Redis-backed product cache
  - Redis-backed logout blacklist
  - Redis-backed gateway rate limiting
  - RabbitMQ-backed order event flow
  - outbox-backed event staging in `order-service`
  - MongoDB-backed order event timeline sink
  - Docker Compose
  - unified production polish
- Phase 3:
  - Kubernetes
  - monitoring
  - load testing
  - AWS migration notes
  - CI/CD baseline

Phase 3 has now started with:

- a Kubernetes sandbox manifest baseline under `infra/k8s/sandbox`
- runtime-configurable frontend API routing for ingress-based deployments
- Prometheus / Grafana bootstrap assets under `infra/monitoring`
- starter `k6` load-test scripts under `scripts/load`
- a first GitHub Actions CI workflow under `.github/workflows/ci.yml`
- expanded AWS production migration notes under `infra/aws/prod`

## Screenshots

### Sign-In

![Sign-In Page](docs/screenshots/login-page.png)

### Admin Order Review

![Admin Order Review](docs/screenshots/order-review-page.png)

### Product Listing

![Product Listing](docs/screenshots/products-page.png)

### Product Admin

![Product Admin](docs/screenshots/product-admin-page.png)

## Architecture Overview

Detailed cross-service message flow diagrams live in [docs/architecture.md](docs/architecture.md), including the RabbitMQ main chain, reliability side chain, and routing fan-out view.

Phase 3 deployment and performance notes now also include:

- [docs/pressure-test.md](docs/pressure-test.md)
- [docs/sandbox-operations-runbook.md](docs/sandbox-operations-runbook.md)
- [infra/monitoring/README.md](infra/monitoring/README.md)
- [infra/k8s/sandbox/README.md](infra/k8s/sandbox/README.md)
- [infra/aws/prod/README.md](infra/aws/prod/README.md)

### Services

- `frontend`
  - React application for user and admin flows
- `gateway`
  - FastAPI gateway for routing, JWT verification, and request user context propagation
- `services/user-service`
  - authentication and user profile management
- `services/product-service`
  - product listing, product administration, stock mutation APIs
- `services/order-service`
  - order creation, cancellation, and admin approval / rejection
- `services/notification-service`
  - RabbitMQ consumer for order lifecycle notifications and MongoDB-backed audit sink

### Data and Infra

- MySQL
  - `h_user_db`
  - `h_product_db`
  - `h_order_db`
- Redis
  - active for product catalog caching in `product-service`
  - active for JWT blacklist support in `user-service`
  - active for gateway login and order-create rate limiting
- RabbitMQ
  - active for outbox-relayed order lifecycle event fan-out from `order-service`
  - active for the lightweight `notification-service` consumer
- MongoDB
  - active for the optional `order_event_timeline` audit sink in `notification-service`
  - reserved for side-channel audit logs and notification records
  - intentionally kept out of the critical relational transaction path

## Tech Stack

### Frontend

- React 18
- Vite
- TypeScript
- Ant Design
- Axios
- React Router

### Backend

- FastAPI
- SQLAlchemy
- Spring Boot
- Spring Data JPA
- Spring Security
- springdoc-openapi

### Infrastructure

- MySQL 8
- Redis 7
- RabbitMQ 3
- lightweight notification worker
- Docker / Docker Compose
- Kubernetes sandbox baseline now available under `infra/k8s/sandbox`

## Repository Structure

```text
modern-user-product-order-system/
├── frontend/
├── gateway/
├── services/
│   ├── user-service/
│   ├── product-service/
│   ├── order-service/
│   └── notification-service/
├── docs/
├── infra/
│   ├── docker/
│   ├── k8s/
│   └── aws/
├── scripts/
└── .github/workflows/
```

## Environment Strategy

- `dev`
  - day-to-day developer iteration
  - may use direct local process startup or `infra/docker/docker-compose.dev.yml`
  - expects host-managed infrastructure when using the lightweight Compose stack
- `sandbox`
  - full integration and demo environment
  - uses `infra/docker/docker-compose.sandbox.yml`
  - includes MySQL, Redis, RabbitMQ, and MongoDB containers
- `prod`
  - reserved for future Kubernetes and cloud deployment
  - configuration placeholders live under `infra/k8s/` and `infra/aws/`

## Branching and Promotion Strategy

This repository is intended to follow three long-lived branches that mirror the three environment tiers:

- `dev`
  - the active development trunk
  - feature work should branch from `dev`
  - new work is merged back into `dev` first
- `sandbox`
  - the integration, demo, and pre-release validation branch
  - changes are promoted from `dev` into `sandbox` after a coherent feature batch is ready for end-to-end verification
- `main`
  - the most stable showcase branch
  - intended to represent the latest approved release candidate for portfolio presentation and future production promotion

Recommended branch flow:

```text
feature/* -> dev -> sandbox -> main
```

Recommended collaboration rules:

- create feature branches from `dev`
- open pull requests back into `dev` for day-to-day implementation work
- promote `dev` into `sandbox` when the integration set is ready for smoke tests, screenshots, and demo review
- promote `sandbox` into `main` only after validation passes
- treat `main` as the branch that should stay the cleanest and most presentation-ready

Current practical meaning:

- merging into `dev` means the work is ready for developer iteration
- merging into `sandbox` means the work is ready for full Compose-based integration and demo validation
- merging into `main` means the work is stable enough to be presented as the current best version of the project

Future CI/CD mapping:

- `dev`
  - run build, lint, unit tests, and service-level checks
- `sandbox`
  - run integration build, smoke test, and sandbox deployment
- `main`
  - run release build and future production deployment workflow

## Local Run

### Sandbox Compose Run

From the repository root:

```bash
docker compose --env-file infra/docker/.env.sandbox.example -f infra/docker/docker-compose.sandbox.yml up --build
```

This stack starts:

- frontend
- gateway
- user-service
- product-service
- order-service
- notification-service
- mysql
- redis
- rabbitmq
- mongodb

The MySQL container initializes the three schemas, core tables, demo users, and sample products on first boot.

### Dev Compose Run

From the repository root:

```bash
docker compose --env-file infra/docker/.env.dev.example -f infra/docker/docker-compose.dev.yml up --build
```

This lightweight stack expects host-managed infrastructure, such as your existing local MySQL and RabbitMQ containers.

### Monitoring Compose Run

From the repository root:

```bash
docker compose --env-file infra/docker/.env.monitoring.example -f infra/docker/docker-compose.monitoring.yml up -d
```

Default local URLs:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

### 1. Start the backend services

Gateway:

```bash
cd gateway
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

User service:

```bash
cd services/user-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8001
```

Product service:

```bash
cd services/product-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8002
```

Order service:

```bash
cd services/order-service
mvn spring-boot:run
```

Notification service:

```bash
cd services/notification-service
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python -m app.main
```

### 2. Start the frontend

```bash
cd frontend
npm install
npm run dev
```

Frontend URL:

- `http://localhost:5173`

### 3. Run the Phase 1 smoke test

After the four backend services are up, run:

```bash
python3 scripts/dev/smoke-test-phase1.py
```

To run the same smoke test against the dev compose stack:

```bash
SMOKE_TEST_BASE_URL=http://127.0.0.1:8010 python3 scripts/dev/smoke-test-phase1.py
```

### 4. Run the Phase 2 Redis smoke test

After Redis-backed logout revocation is enabled, run:

```bash
python3 scripts/dev/smoke-test-phase2.py
```

To run it against the dev compose stack:

```bash
SMOKE_TEST_BASE_URL=http://127.0.0.1:8010 python3 scripts/dev/smoke-test-phase2.py
```

This validates:

- user and admin login
- product listing
- create order
- cancel order
- approve order
- reject order
- latest-first order sorting
- pending review queue behavior

The script writes sample orders into the local development database so the review and history screens show realistic data.

## Local Access Points

Sandbox:

- Frontend: `http://localhost:5173`
- User service docs: `http://localhost:8001/docs`
- Product service docs: `http://localhost:8002/docs`
- Order service docs: `http://localhost:8080/swagger-ui/index.html`
- Sandbox MySQL: `localhost:3307`
- Sandbox Redis: `localhost:6380`
- Sandbox RabbitMQ AMQP: `localhost:5673`
- Sandbox RabbitMQ management: `http://localhost:15673`
- Sandbox MongoDB: `mongodb://admin:admin123@localhost:27018`

Dev compose:

- Frontend: `http://localhost:5174`
- User service docs: `http://localhost:8011/docs`
- Product service docs: `http://localhost:8012/docs`
- Order service docs: `http://localhost:8081/swagger-ui/index.html`

## New Branch Development Runbook

Use this flow when following the long-lived `dev -> sandbox -> main` promotion model and starting a new batch of implementation work.

### 1. Refresh local `main`

```bash
git checkout main
git pull origin main
```

### 2. Sync `dev` with the latest `main`

```bash
git checkout dev
git merge main
git push origin dev
```

### 3. Create a new feature branch from `dev`

Replace `dev-xxx` with the actual workstream name, for example `dev-rabbitmq-events`.

```bash
git checkout dev
git checkout -b dev-xxx
git push -u origin dev-xxx
```

### 4. Implement and validate on the feature branch

Typical checks during development:

- prefer validating feature work in the local `dev` runtime first
- use `infra/docker/docker-compose.dev.yml` when the batch depends on host-managed local services such as `local-mysql` or `rmq`
- run local service-specific checks
- run `python3 scripts/dev/smoke-test-phase1.py` when the core order flow is affected
- run `python3 scripts/dev/smoke-test-phase2.py` when Redis-backed auth or rate limiting is affected
- inspect `modern-upo-dev-notification-service` logs when RabbitMQ event flow changes
- run `python3 scripts/dev/smoke-test-rabbitmq-dev.py` when validating local RabbitMQ event flow against IDE-started services

### 5. Merge the feature branch back into `dev`

```bash
git checkout dev
git merge dev-xxx
git push origin dev
```

### 6. Promote the validated `dev` branch into `sandbox`

After the `dev` branch is stable, continue with the `Sandbox Promotion Runbook` above.

## Sandbox Promotion Runbook

Use this flow when a batch of work is ready to move from `dev` into `sandbox` for integration verification.

### 1. Switch to `sandbox` and merge `dev`

```bash
git checkout sandbox
git merge dev
```

### 2. Start or refresh the sandbox stack

```bash
docker compose --env-file infra/docker/.env.sandbox.example -f infra/docker/docker-compose.sandbox.yml up -d --build
```

### 3. Run the Phase 1 business smoke test

```bash
python3 scripts/dev/smoke-test-phase1.py
```

### 4. Run the Phase 2 Redis smoke test

```bash
python3 scripts/dev/smoke-test-phase2.py
```

### 5. Perform manual sandbox checks

Open and verify:

- Frontend: `http://localhost:5173`
- User service docs: `http://localhost:8001/docs`
- Product service docs: `http://localhost:8002/docs`
- Order service docs: `http://localhost:8080/swagger-ui/index.html`

Recommended manual checks:

- sign in as `john_smith` and confirm product browsing still works
- sign in as `admin` and confirm order review and product admin pages still load
- sign out once and confirm the session is cleared cleanly

### 6. Perform monitoring checks

If the monitoring stack is part of the current validation batch, start or refresh it:

```bash
docker compose --env-file infra/docker/.env.monitoring.example -f infra/docker/docker-compose.monitoring.yml up -d
```

Open and verify:

- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`

Reference screenshots:

![Prometheus Targets](docs/screenshots/screen_prometheus.png)

![Grafana Overview](docs/screenshots/screen_grafana.png)

Grafana default credentials:

- username: `admin`
- password: `admin123`

Recommended monitoring checks:

- in Prometheus, open `Status` -> `Targets` and confirm the sandbox targets on `localhost:8000`, `localhost:8001`, `localhost:8002`, and `localhost:8080` are `UP`
- in Prometheus, run the `up` query and confirm the sandbox service metrics are visible
- in Grafana, open the provisioned `Modern UPO Overview` dashboard and confirm panels load without datasource errors

### 7. Push the validated `sandbox` branch

```bash
git push origin sandbox
```

After the sandbox branch is validated and pushed, open the pull request from `sandbox` into `main`.

## Demo Accounts

- Admin: `admin / Admin@123`
- Demo user: `john_smith / User@123`

## Important Local Notes

- The JWT secret used by the gateway and user-service must match.
- The gateway and user-service now also share Redis-backed token revocation state.
- The MySQL application user must have access to:
  - `h_user_db`
  - `h_product_db`
  - `h_order_db`
- The frontend currently assumes the gateway is reachable at `http://localhost:8000`.
- The current UI is English-only for now. Internationalization can be added later.
- MongoDB is a planned side-channel data store for Phase 2 and later, not the source of truth for user, product, or order records.
- The sandbox Compose stack uses `infra/docker/mysql/init/01-init.sql` to provision fresh local data on first database startup.
- The intended long-lived branch strategy is `feature/* -> dev -> sandbox -> main`.

## Documentation

- [Architecture](docs/architecture.md)
- [Database Design](docs/database-design.md)
- [API Overview](docs/api-overview.md)
- [Infra Overview](infra/README.md)
- [Frontend README](frontend/README.md)
- [Gateway README](gateway/README.md)
- [User Service README](services/user-service/README.md)
- [Product Service README](services/product-service/README.md)
- [Order Service README](services/order-service/README.md)

## Near-Term Next Steps

- add Docker Compose for one-command local startup
- integrate Redis caching
- integrate RabbitMQ domain events
- add Kubernetes manifests
- add monitoring and load-test artifacts
