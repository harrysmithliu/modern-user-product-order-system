# Infrastructure Environments

This project is organized around three environment tiers plus one dedicated
always-on demo deployment track:

- `dev`
  - fast local iteration
  - application containers can reuse host-managed infrastructure
  - current Docker entry: `infra/docker/docker-compose.dev.yml`
- `sandbox`
  - full integration and demo environment
  - includes frontend, gateway, core services, notification worker, MySQL, Redis, RabbitMQ, and MongoDB
  - current Docker entry: `infra/docker/docker-compose.sandbox.yml`
- `sandbox-ec2-online`
  - low-cost long-running online environment
  - intended for one EC2 host with Docker Compose, Nginx, Let's Encrypt, and GitHub Actions deployment
  - deployment files live under `infra/aws/sandbox-ec2`
- `prod`
  - reserved for future Kubernetes and cloud deployment
  - production manifests and cloud setup notes will live under `infra/k8s/` and `infra/aws/`

At the current stage:

- `dev` is available through a lightweight Compose stack or direct local process startup
- `sandbox` is the recommended environment for end-to-end demo validation
- `sandbox-ec2-online` is the recommended branch/runtime pairing for a low-cost public demo environment
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

## AWS EC2 Online Demo Baseline

For a low-cost always-on deployment path, this repository now also includes:

- `infra/aws/sandbox-ec2`

This track is intentionally different from the EKS production baseline:

- it runs the full stack on one EC2 host
- it uses Docker Compose instead of Kubernetes
- it uses Nginx as the public reverse proxy
- it is designed for a long-running sandbox-like public demo
- it includes a GitHub Actions deployment workflow for the `sandbox-ec2-online` branch

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
- `sandbox-ec2-online` maps to the `sandbox-ec2-online` branch
  - used as the long-running public demo deployment source

Recommended promotion path for the core delivery line:

```text
feature/* -> dev -> sandbox -> main
```

In practice:

- developers branch from `dev`
- completed feature branches are merged into `dev`
- validated batches are promoted from `dev` to `sandbox`
- only approved sandbox states are promoted from `sandbox` to `main`

This keeps the branch model aligned with the runtime environment model and makes future CI/CD mapping much easier.

The low-cost online deployment line is intentionally separate:

```text
sandbox -> sandbox-ec2-online
```

That keeps the public demo branch close to the validated sandbox runtime while
letting `main` continue to represent the cloud-native production direction.
