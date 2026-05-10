# Agent Development Responsibilities

This repository uses an agent-oriented delivery model to keep development ownership clear while preserving one coherent system architecture.

## Coordination Model

The `Project Main Developer` owns architecture direction, task sequencing, cross-agent integration, and final delivery decisions. Specialist agents own implementation within their domains and should keep changes aligned with existing repository conventions.

All agents should:

- keep changes scoped to their domain unless cross-domain integration is required
- preserve the branch promotion flow documented in `README.md`
- avoid committing secrets, generated runtime state, or local environment files
- update documentation when behavior, setup, deployment, or operational workflow changes
- prefer existing scripts, conventions, and service boundaries before introducing new patterns

## Frontend

`Frontend Agent` owns:

- React, Vite, TypeScript, Ant Design, and frontend routing
- user and admin flows
- API integration through the gateway
- UI polish, screenshots, and browser-level verification when frontend behavior changes

## Backend Services

`Java Agent` owns:

- `services/order-service`
- Spring Boot business logic
- order lifecycle behavior
- concurrency, JVM, persistence, and Java test coverage

`Python Agent` owns:

- `gateway`
- `services/user-service`
- `services/product-service`
- `services/notification-service`
- FastAPI service behavior, Python tests, and lightweight async/runtime integration

`RabbitMQ Agent` owns:

- order event publishing and consumption
- outbox-style message flow
- routing keys, queues, exchanges, and message delivery verification

## Data Layer

`Redis Agent` owns:

- product cache behavior
- logout/session revocation support
- gateway rate limiting
- Redis configuration and validation scripts

`MongoDB Agent` owns:

- notification audit persistence
- order event timeline storage
- MongoDB local, sandbox, and cloud configuration touchpoints

## Infrastructure

`DevOps Agent` owns:

- Dockerfiles and Docker Compose environments
- local, dev, sandbox, and EC2 runtime workflows
- environment promotion runbooks

`K8s Agent` owns:

- Kubernetes manifests
- sandbox and production deployment baselines
- Kubernetes apply validation notes

`AWS Agent` owns:

- AWS deployment scripts
- EC2 online demo path
- EKS / production migration assets
- cloud cost and operational setup notes

`CI/CD Agent` owns:

- GitHub Actions workflows
- shared CI validation
- split deployment workflows for EC2 and AWS production paths

`AppSec Agent` owns:

- secret handling guidance
- API exposure controls
- security-oriented configuration hardening

## Post-Release

`SRE Agent` owns:

- health checks
- observability assets
- Prometheus / Grafana readiness
- operational verification and uptime-oriented checks

`Debug Agent` owns:

- issue reproduction
- runtime diagnosis
- local and remote troubleshooting notes

`Presentation Agent` owns:

- README polish
- screenshots
- architecture diagrams
- demo narrative

`Interview Agent` owns:

- resume-oriented phrasing
- interview talking points
- project storytelling aligned with the implemented system
