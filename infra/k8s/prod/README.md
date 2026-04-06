# Kubernetes Production Baseline

This directory contains the first production-oriented Kubernetes manifest
skeleton for the project.

Unlike `infra/k8s/sandbox`, the production flavor assumes:

- application workloads run inside the cluster
- MySQL, Redis, RabbitMQ, and MongoDB are provided by managed external services
- ingress is expected to be backed by AWS ALB
- image tags are production-specific
- production secrets and hostnames are injected before deployment

## Included Resources

- `namespace.yaml`
  - production namespace
- `configmap-app.yaml`
  - external dependency hosts, runtime tuning, and ingress host placeholders
- `configmap-frontend.yaml`
  - frontend runtime API routing
- `secret-app.yaml`
  - secret placeholders for JWT, MySQL, RabbitMQ, and MongoDB
- `frontend.yaml`
- `gateway.yaml`
- `user-service.yaml`
- `product-service.yaml`
- `order-service.yaml`
- `notification-service.yaml`
- `ingress.yaml`
  - ALB-oriented ingress baseline
- `hpa.yaml`
  - higher production autoscaling baseline
- `kustomization.yaml`

## Key Differences From Sandbox

- no in-cluster MySQL, Redis, RabbitMQ, or MongoDB Deployments
- stronger replica counts and resource requests/limits
- ALB ingress annotations instead of local nginx assumptions
- image tags use `:prod` placeholders
- secret and hostname values are explicitly production placeholders

## Build

From the repository root:

```bash
kubectl kustomize infra/k8s/prod
```

This confirms the production manifest set is structurally valid before a real EKS rollout.

## Apply Model

The intended production apply flow is:

1. build and push the release image set to ECR
2. source the AWS production env file
3. update kubeconfig for the target EKS cluster
4. populate real secret values
5. apply `infra/k8s/prod`
6. wait for rollout completion
7. validate ingress, health, and metrics

This skeleton is designed for:

- `kubectl apply -k infra/k8s/prod`
- image replacement through manifest edits or CI
- ALB-backed ingress exposure
- external managed data services

## Runtime Inputs That Must Be Replaced

Before a real deployment, replace or inject:

- placeholder image tags
- ingress hostname
- JWT secret
- MySQL host, port, username, password
- Redis host and port
- RabbitMQ host, port, username, password
- MongoDB URI and credentials if the audit sink is enabled

These values are intentionally still placeholders in the repo because this
directory is a deployment skeleton, not a live production environment.

## Relationship to AWS Scripts

The production Kubernetes skeleton is designed to pair with:

- `infra/aws/prod/deploy-ecr.sh`
- `infra/aws/prod/deploy-eks.sh`

Recommended workflow:

1. push production-tagged images to ECR
2. replace placeholder hostnames, secrets, and image tags
3. apply `infra/k8s/prod`
4. verify ALB ingress, health, and metrics

## Minimal Verification Checklist

After applying the production manifests, verify:

```bash
kubectl get pods,svc,ingress -n modern-upo-prod
kubectl rollout status deployment/frontend -n modern-upo-prod
kubectl rollout status deployment/gateway -n modern-upo-prod
kubectl rollout status deployment/user-service -n modern-upo-prod
kubectl rollout status deployment/product-service -n modern-upo-prod
kubectl rollout status deployment/order-service -n modern-upo-prod
kubectl rollout status deployment/notification-service -n modern-upo-prod
```

Then confirm:

- the ALB ingress has been provisioned
- the frontend is reachable through the production host
- `/api` routes are reaching the gateway
- service-to-dependency connections succeed with real external hosts
- metrics endpoints are reachable from the cluster network

## Next Enhancements

- External Secrets integration
- TLS certificate wiring
- RDS and ElastiCache parameter templating
- ECR tag injection through CI/CD
- environment overlays for `sandbox` and `prod`
