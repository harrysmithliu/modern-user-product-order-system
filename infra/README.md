# Infrastructure Environments

This project is organized around three environment tiers:

- `dev`
  - fast local iteration
  - application containers can reuse host-managed infrastructure
  - current Docker entry: `infra/docker/docker-compose.dev.yml`
- `sandbox`
  - full integration and demo environment
  - includes frontend, gateway, core services, notification worker, MySQL, Redis, RabbitMQ, and MongoDB
  - current Docker entry: `infra/docker/docker-compose.sandbox.yml`
- `prod`
  - reserved for future Kubernetes and cloud deployment
  - production manifests and cloud setup notes will live under `infra/k8s/` and `infra/aws/`

At the current stage:

- `dev` is available through a lightweight Compose stack or direct local process startup
- `sandbox` is the recommended environment for end-to-end demo validation
- `prod` is intentionally reserved as a deployment target, not yet implemented

## Branch Mapping

The environment layout is intended to match the Git branching model:

- `dev` environment maps to the `dev` branch
  - used for daily development and short feedback loops
- `sandbox` environment maps to the `sandbox` branch
  - used for integrated verification, demo review, and release candidate checks
- `prod` environment maps to the `main` branch
  - used as the long-term stable branch and future production release source

Recommended promotion path:

```text
feature/* -> dev -> sandbox -> main
```

In practice:

- developers branch from `dev`
- completed feature branches are merged into `dev`
- validated batches are promoted from `dev` to `sandbox`
- only approved sandbox states are promoted from `sandbox` to `main`

This keeps the branch model aligned with the runtime environment model and makes future CI/CD mapping much easier.
