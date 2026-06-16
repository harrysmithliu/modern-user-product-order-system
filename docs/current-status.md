# Current Status

The repository is under phased implementation.

## Phase 1

- login
- profile update
- password change
- product listing
- create order
- cancel order
- admin order review
- admin product management

## Phase 2

- Redis-backed product cache
- Redis-backed logout blacklist
- Redis-backed gateway rate limiting
- RabbitMQ-backed order event flow
- outbox-backed event staging in `order-service`
- MongoDB-backed order event timeline sink
- Docker Compose
- unified production polish

## Phase 3

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
