# AWS Production Deployment Baseline

This directory contains the first practical AWS deployment baseline for the
project.

It is intended to bridge the gap between:

- local `kind` validation
- the sandbox Docker and Kubernetes baselines
- a future real AWS production rollout

The current baseline does not provision cloud infrastructure automatically, but
it now provides the release-time pieces needed to move from local validation to
an actual AWS deployment workflow:

- an AWS environment template
- an ECR image push script
- an EKS deployment script
- a release checklist

## Included Files

- `env.example`
  - example environment values for AWS account, region, ECR repository names, and EKS cluster settings
- `deploy-ecr.sh`
  - logs in to ECR, ensures repositories exist, tags local images, and pushes them
- `deploy-eks.sh`
  - updates kubeconfig, creates namespace and secrets, and applies a production manifest set placeholder
- `checklist.md`
  - a practical pre-flight and post-deploy checklist for AWS rollout

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
  - image in ECR
  - workload on EKS
  - exposed through ALB ingress
- gateway
  - image in ECR
  - workload on EKS
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

## Local Preparation

Before using the scripts in this directory:

1. install and configure the AWS CLI
2. authenticate with an AWS account that can manage ECR and EKS
3. copy `env.example` to a local, untracked env file such as `.env.prod.local`
4. update the values for:
   - AWS account id
   - AWS region
   - EKS cluster name
   - image tag
   - secrets

Example:

```bash
cp infra/aws/prod/env.example infra/aws/prod/.env.prod.local
```

## Standard Release Flow

The intended release sequence is:

1. validate the batch locally on `dev`
2. promote it to `sandbox` and complete smoke, monitoring, and load-test checks
3. build the final release image set
4. export AWS environment values
5. push images to ECR
6. update kubeconfig for the target EKS cluster
7. apply the production Kubernetes manifests
8. validate health, ingress, and metrics

## ECR Image Push

From the repository root:

```bash
source infra/aws/prod/.env.prod.local
bash infra/aws/prod/deploy-ecr.sh
```

This script:

- verifies required local images exist
- logs in to ECR
- creates missing repositories
- tags local images with the requested release tag
- pushes the images

Expected local image set:

- `upo-frontend:<tag>`
- `upo-gateway:<tag>`
- `upo-user-service:<tag>`
- `upo-product-service:<tag>`
- `upo-order-service:<tag>`
- `upo-notification-service:<tag>`

## EKS Deployment

From the repository root:

```bash
source infra/aws/prod/.env.prod.local
bash infra/aws/prod/deploy-eks.sh
```

This script currently provides the minimal deployment skeleton:

- updates local kubeconfig for the target EKS cluster
- verifies cluster reachability
- creates the target namespace if needed
- applies a production secret placeholder
- applies the production manifest set from `infra/k8s/prod`

`infra/k8s/prod` is still intentionally lighter than a full production chart set,
so this script is best understood as a deployment baseline rather than a complete release pipeline.

## Suggested Promotion Path

1. validate locally on `dev`
2. promote to `sandbox`
3. validate sandbox smoke tests and monitoring
4. build and tag release images
5. push images to ECR
6. apply EKS production manifests
7. validate ingress, health, and metrics

## Post-Deploy Verification

After the EKS apply step, verify at minimum:

- the target namespace exists
- the frontend, gateway, core services, and notification worker are ready
- the ALB ingress has an address
- the gateway health endpoint responds
- Prometheus-style metrics endpoints are reachable from inside the cluster
- the app secrets and ConfigMaps were populated with real values

Suggested checks:

```bash
kubectl config current-context
kubectl get ns
kubectl get pods,svc,ingress -n "${K8S_NAMESPACE}"
kubectl rollout status deployment/gateway -n "${K8S_NAMESPACE}"
kubectl rollout status deployment/order-service -n "${K8S_NAMESPACE}"
kubectl describe ingress modern-upo-prod -n "${K8S_NAMESPACE}"
```

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

## Relationship to Production Manifests

The scripts in this directory are meant to be used together with:

- [infra/k8s/prod/README.md](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/infra/k8s/prod/README.md)
- [infra/k8s/prod/kustomization.yaml](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/infra/k8s/prod/kustomization.yaml)

The AWS scripts handle:

- registry push
- cluster access
- namespace and secret bootstrap
- manifest apply

The Kubernetes production skeleton handles:

- Deployments
- Services
- ingress
- HPA
- runtime configuration wiring

## Current Limits

- the repository does not yet provision AWS infrastructure itself
- TLS certificate management is still a follow-up task
- RDS, ElastiCache, Amazon MQ, and MongoDB service wiring still require real production values
- CI-driven tag injection and full rollout automation remain the next enhancement layer
