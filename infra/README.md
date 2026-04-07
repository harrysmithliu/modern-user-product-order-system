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

## Kubernetes Baseline

Phase 3 now includes an initial Kubernetes sandbox baseline under:

- `infra/k8s/sandbox`

The current manifest set includes:

- namespace
- ConfigMap and Secret
- MySQL, Redis, RabbitMQ, and MongoDB deployments
- frontend, gateway, user-service, product-service, order-service, and notification-service deployments
- ingress
- HPA stubs

Apply with:

```bash
kubectl apply -k infra/k8s/sandbox
```

Repeatable local kind validation is also available through:

```bash
bash scripts/dev/validate-k8s-sandbox-kind.sh
```

## Monitoring Compose

Phase 3 also includes a local monitoring stack:

- `infra/docker/docker-compose.monitoring.yml`

Start it with:

```bash
docker compose --env-file infra/docker/.env.monitoring.example -f infra/docker/docker-compose.monitoring.yml up -d
```

## AWS Deployment Baseline

Phase 3 now also includes an AWS deployment skeleton under:

- `infra/aws/prod`

This baseline currently provides:

- an AWS environment template
- an ECR push script
- an EKS deployment script
- a release checklist

These files are designed to turn the current local Kubernetes validation into a repeatable AWS migration path once real AWS credentials and cluster resources are available.

## Shared CI and split CD

The infrastructure automation now follows a shared validation layer with two separate deployment targets:

- shared CI
  - [ci.yml](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/.github/workflows/ci.yml) validates the frontend, Python services, and Java order service across the mainline branches and the EC2 demo branch
- EC2 online demo CD
  - [deploy-sandbox-ec2.yml](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/.github/workflows/deploy-sandbox-ec2.yml) deploys the `sandbox-ec2-online` branch to the long-running EC2 demo environment
- AWS prod CD baseline
  - [deploy-aws-prod.yml](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/.github/workflows/deploy-aws-prod.yml) is a manual production deployment skeleton for future `main` branch ECR and EKS rollout automation

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
