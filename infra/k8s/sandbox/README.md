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

## Local kind Validation

For a repeatable local validation flow, use:

```bash
bash scripts/dev/validate-k8s-sandbox-kind.sh
```

By default this script will:

- create the `kind-modern-upo` cluster if it does not exist
- load the local `upo-*:sandbox` images into the kind nodes
- apply the full sandbox manifest set
- wait for the 10 core Deployments to become ready
- print pod, service, and ingress status

Optional environment overrides:

- `KIND_CLUSTER_NAME`
- `K8S_SANDBOX_NAMESPACE`
- `CREATE_KIND_CLUSTER_IF_MISSING`
- `LOAD_SANDBOX_IMAGES`
- `ROLLOUT_TIMEOUT`
- `PORT_FORWARD_GATEWAY`
- `PORT_FORWARD_PORT`

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

The validation script above performs this load step automatically when `LOAD_SANDBOX_IMAGES=true`.

## Frontend Runtime Config

The frontend now supports a runtime `config.js` override. In Kubernetes, the
manifest mounts a ConfigMap that sets:

- `apiBaseUrl: "/api"`

This allows the same built frontend image to work behind ingress without being
hard-wired to `localhost:8000`.

The current sandbox environment is implemented through:

- `infra/docker/docker-compose.sandbox.yml`

For remote Kubernetes day-2 operations such as rollout, restart, rollback, and
health-check procedure, see:

- `docs/sandbox-operations-runbook.md`
