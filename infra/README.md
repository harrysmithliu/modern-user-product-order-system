# Infrastructure Environments

This project is organized around three environment tiers:

- `dev`
  - fast local iteration
  - application containers can reuse host-managed infrastructure
  - current Docker entry: `infra/docker/docker-compose.dev.yml`
- `sandbox`
  - full integration and demo environment
  - includes application services plus MySQL, Redis, RabbitMQ, and MongoDB
  - current Docker entry: `infra/docker/docker-compose.sandbox.yml`
- `prod`
  - reserved for future Kubernetes and cloud deployment
  - production manifests and cloud setup notes will live under `infra/k8s/` and `infra/aws/`

At the current stage:

- `dev` is available through a lightweight Compose stack or direct local process startup
- `sandbox` is the recommended environment for end-to-end demo validation
- `prod` is intentionally reserved as a deployment target, not yet implemented
