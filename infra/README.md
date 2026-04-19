# Infrastructure Environments

This project is organized around a primary AWS / EKS production line plus a secondary EC2 online demo line:

- `dev`
  - fast local iteration
  - application containers can reuse host-managed infrastructure
  - current Docker entry: `infra/docker/docker-compose.dev.yml`
- `sandbox`
  - full integration and demo environment
  - includes frontend, gateway, core services, notification worker, MySQL, Redis, RabbitMQ, and MongoDB
  - current Docker entry: `infra/docker/docker-compose.sandbox.yml`
- `prod`
  - AWS / EKS baseline for the main deployment line
  - production manifests and cloud setup notes live under `infra/k8s/prod/` and `infra/aws/prod/`
- `sandbox-ec2-online`
  - auxiliary low-cost public demo line
  - deployment files live under `infra/aws/sandbox-ec2`

At the current stage:

- `dev` is available through a lightweight Compose stack or direct local process startup
- `sandbox` is the recommended environment for end-to-end demo validation
- `prod` is the AWS / EKS target for the main delivery line
- `sandbox-ec2-online` is the cost-efficient public demo target for the auxiliary deployment line

## Kubernetes Baseline

Phase 3 now includes the Kubernetes baseline for the AWS / EKS line under:

- `infra/k8s/prod`

The current manifest set includes the production-oriented Kubernetes resources and rollout skeletons used by the main line:

- namespace
- ConfigMap and Secret
- MySQL, Redis, RabbitMQ, and MongoDB deployments
- frontend, gateway, user-service, product-service, order-service, and notification-service deployments
- ingress
- HPA stubs

Apply with:

```bash
kubectl apply -k infra/k8s/prod
```

Repeatable local kind validation is also available through:

```bash
bash scripts/dev/validate-k8s-sandbox-kind.sh
```

## EC2 Online Demo Baseline

The auxiliary public demo line uses a single EC2 host under:

- `infra/aws/sandbox-ec2`

This track is intentionally different from the AWS / EKS production baseline:

- it runs the full stack on one EC2 host
- it uses Docker Compose instead of Kubernetes
- it uses Nginx as the public reverse proxy
- it is designed for a long-running sandbox-like public demo
- it includes a GitHub Actions deployment workflow for the `sandbox-ec2-online` branch

## Monitoring Compose

Phase 3 also includes a local monitoring stack:

- `infra/docker/docker-compose.monitoring.yml`

Start it with:

```bash
docker compose --env-file infra/docker/.env.monitoring.example -f infra/docker/docker-compose.monitoring.yml up -d
```

## AWS Deployment Baseline

Phase 3 now also includes an AWS deployment skeleton for the main line under:

- `infra/aws/prod`

This baseline currently provides:

- an AWS environment template
- an ECR push script
- an EKS deployment script
- a release checklist

These files are designed to turn the current local Kubernetes validation into a repeatable AWS / EKS migration path once real AWS credentials and cluster resources are available.

## Shared CI and split CD

The infrastructure automation now follows a shared validation layer with two separate deployment targets:

- shared CI
  - [ci.yml](../.github/workflows/ci.yml) validates the frontend, Python services, and Java order service across the mainline branches and the EC2 demo branch
- AWS / EKS deployment route
  - [deploy-aws-prod.yml](../.github/workflows/deploy-aws-prod.yml) is a manual production deployment skeleton for the `main` branch ECR and EKS rollout automation
- EC2 online demo CD
  - [deploy-sandbox-ec2.yml](../.github/workflows/deploy-sandbox-ec2.yml) deploys the `sandbox-ec2-online` branch to the long-running EC2 demo environment

## Branch Mapping

The environment layout is intended to match the Git branching model:

- `dev` environment maps to the `dev` branch
  - used for daily development and short feedback loops
- `sandbox` environment maps to the `sandbox` branch
  - used for integrated verification, demo review, and release candidate checks
- `prod` environment maps to the `main` branch
  - used as the long-term stable branch and AWS / EKS production release source

Auxiliary demo branch mapping:

- `sandbox-ec2-online` maps to the `sandbox-ec2-online` branch
  - used as the long-running public demo deployment source

Recommended promotion path:

```text
feature/* -> dev -> sandbox -> main
```

Supplementary demo promotion path:

```text
sandbox -> sandbox-ec2-online
```

In practice:

- developers branch from `dev`
- completed feature branches are merged into `dev`
- validated batches are promoted from `dev` to `sandbox`
- only approved sandbox states are promoted from `sandbox` to `main`
- sandbox-approved demo changes can be selectively cherry-picked into `sandbox-ec2-online`

This keeps the branch model aligned with the runtime environment model and makes future CI/CD mapping much easier.
