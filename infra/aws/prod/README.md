# AWS Production Migration Notes

This directory documents the intended production migration path for the project.

The current repository does not yet contain full production deployment automation,
but the target architecture is now defined clearly enough for Phase 3 planning.

## Target AWS Services

- ECR
  - container image registry for frontend and service images
- EKS
  - Kubernetes runtime for gateway, core services, notification worker, and supporting manifests
- ALB
  - ingress entrypoint for frontend and `/api` routing
- RDS MySQL
  - replacement for the local single-container MySQL runtime
- ElastiCache Redis
  - replacement for the local Redis runtime
- Amazon MQ or self-managed RabbitMQ on Kubernetes
  - production message broker choice for order event delivery
- DocumentDB or self-managed MongoDB on Kubernetes
  - optional production target for the audit sink, depending on compatibility requirements

## Proposed Production Mapping

- frontend
  - container image in ECR
  - deployed on EKS
  - exposed through ALB ingress
- gateway
  - container image in ECR
  - deployed on EKS
  - public `/api` entrypoint behind ALB
- user-service / product-service / order-service / notification-service
  - internal EKS workloads
- MySQL
  - migrate to RDS MySQL
- Redis
  - migrate to ElastiCache Redis
- RabbitMQ
  - migrate to Amazon MQ or a managed/self-hosted RabbitMQ deployment
- MongoDB audit sink
  - keep optional and side-channel, not on the critical order transaction path

## Suggested Promotion Path

1. build and tag images in CI
2. push images to ECR
3. update Kubernetes manifests or Helm values with release tags
4. deploy to EKS sandbox
5. validate ingress, smoke tests, and observability
6. promote the same image set into production

## Secrets and Configuration

Production should move these values out of example files and into managed secret stores:

- JWT secrets
- MySQL credentials
- Redis credentials
- RabbitMQ credentials
- MongoDB credentials

Recommended options:

- AWS Secrets Manager
- Kubernetes Secrets populated by External Secrets or CI

## Near-Term TODO

- add concrete ECR repository naming conventions
- add sample ALB ingress annotations
- add sample RDS / ElastiCache connection parameter mapping
- add a GitHub Actions deployment workflow for EKS sandbox
