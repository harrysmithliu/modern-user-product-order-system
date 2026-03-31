# Kubernetes Sandbox Manifests

This directory contains the first runnable Kubernetes-oriented sandbox deployment
baseline for the project.

The manifests are designed for:

- local `kind` or `minikube` experimentation
- a future EKS sandbox namespace
- review of service wiring, config separation, ingress shape, and HPA stubs

## Included

- namespace
- shared ConfigMap and Secret
- MySQL, Redis, RabbitMQ, and MongoDB deployments/services
- core application deployments/services
- notification worker deployment
- frontend runtime config injection
- ingress
- HPA stubs for gateway, product-service, and order-service
- `kustomization.yaml` for local rendering

## Apply

```bash
kubectl apply -k infra/k8s/sandbox
```

## Local Hostname

The ingress manifest is written against:

- `sandbox.modern-upo.local`

For local testing, map it to your ingress controller IP or localhost depending on
your cluster setup.

## Image Notes

These manifests currently reference the existing local images:

- `upo-frontend:sandbox`
- `upo-gateway:sandbox`
- `upo-user-service:sandbox`
- `upo-product-service:sandbox`
- `upo-order-service:sandbox`
- `upo-notification-service:sandbox`

For `kind`, you may need to load them into the cluster manually, for example:

```bash
kind load docker-image upo-frontend:sandbox
kind load docker-image upo-gateway:sandbox
kind load docker-image upo-user-service:sandbox
kind load docker-image upo-product-service:sandbox
kind load docker-image upo-order-service:sandbox
kind load docker-image upo-notification-service:sandbox
```

## Frontend Runtime Config

The frontend now supports a runtime `config.js` override. In Kubernetes, the
manifest mounts a ConfigMap that sets:

- `apiBaseUrl: "/api"`

This allows the same built frontend image to work behind ingress without being
hard-wired to `localhost:8000`.

The current sandbox environment is implemented through:

- `infra/docker/docker-compose.sandbox.yml`
