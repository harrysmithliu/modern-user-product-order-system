# Sandbox Operations Runbook

This runbook describes the recommended day-2 operations flow for a remote
Kubernetes-based `sandbox` environment for this project.

It is intentionally aligned with the current repository layout:

- branch-to-environment mapping:
  - `dev -> sandbox -> main`
- Kubernetes baseline:
  - `infra/k8s/sandbox`
- sandbox namespace:
  - `modern-upo-sandbox`

It also reflects an important current boundary in the repository:

- local sandbox startup is already implemented through Docker Compose
- Kubernetes sandbox manifests are implemented as a runnable baseline
- full cloud CI/CD automation is not yet committed in this repository

Use this document as the practical operating model for a cloud-hosted sandbox
until a fully automated pipeline is added.

## Scope

The remote sandbox environment includes:

- frontend
- gateway
- user-service
- product-service
- order-service
- notification-service
- MySQL
- Redis
- RabbitMQ
- MongoDB

The Kubernetes baseline already includes:

- namespace
- ConfigMap and Secret wiring
- Deployments and Services
- ingress
- HPA stubs
- PVCs for MySQL, Redis, RabbitMQ, and MongoDB

## Operating Principles

- treat `sandbox` as an integration and demo environment, not a disposable toy
- prefer declarative rollout with `kubectl apply -k` over one-off manual edits
- restart application Deployments freely when needed, but treat data services
  with more care
- never delete PVCs during routine restart work
- keep release, restart, and rollback steps observable with `rollout status`,
  readiness checks, and smoke tests

## Preconditions

Before operating the remote sandbox, confirm:

- you are on the intended cluster context
- the target namespace is `modern-upo-sandbox`
- container images for the release exist in your registry
- Secrets have been populated with real sandbox credentials
- ingress or port-forward access is available for validation

Useful checks:

```bash
kubectl config current-context
kubectl get ns
kubectl get all -n modern-upo-sandbox
kubectl get pvc -n modern-upo-sandbox
```

## Standard Startup Flow

Use this flow for first deployment into a fresh sandbox namespace or for a
full redeploy after infrastructure drift correction.

### 1. Prepare the release

Recommended release source:

- merge validated work from `dev` into `sandbox`
- build the sandbox image set from the `sandbox` branch state
- push the image set to your registry using an immutable tag such as a Git SHA
  or release timestamp

Suggested image set:

- `upo-frontend`
- `upo-gateway`
- `upo-user-service`
- `upo-product-service`
- `upo-order-service`
- `upo-notification-service`

### 2. Update deployment image references

For a real remote sandbox, prefer immutable release tags rather than reusing
plain `:sandbox` tags.

Recommended approach:

- update the image references in `infra/k8s/sandbox/*.yaml`
- commit the manifest change on the `sandbox` branch
- deploy with `kubectl apply -k`

Emergency alternative for a live cluster:

```bash
kubectl -n modern-upo-sandbox set image deployment/frontend frontend=<registry>/upo-frontend:<release-tag>
kubectl -n modern-upo-sandbox set image deployment/gateway gateway=<registry>/upo-gateway:<release-tag>
kubectl -n modern-upo-sandbox set image deployment/user-service user-service=<registry>/upo-user-service:<release-tag>
kubectl -n modern-upo-sandbox set image deployment/product-service product-service=<registry>/upo-product-service:<release-tag>
kubectl -n modern-upo-sandbox set image deployment/order-service order-service=<registry>/upo-order-service:<release-tag>
kubectl -n modern-upo-sandbox set image deployment/notification-service notification-service=<registry>/upo-notification-service:<release-tag>
```

Use the imperative `set image` path only when you intentionally accept a short
term drift from the manifests stored in Git.

### 3. Apply the sandbox manifests

```bash
kubectl apply -k infra/k8s/sandbox
```

### 4. Wait for the core workloads to become ready

```bash
kubectl rollout status deployment/mysql -n modern-upo-sandbox
kubectl rollout status deployment/redis -n modern-upo-sandbox
kubectl rollout status deployment/rabbitmq -n modern-upo-sandbox
kubectl rollout status deployment/mongodb -n modern-upo-sandbox
kubectl rollout status deployment/user-service -n modern-upo-sandbox
kubectl rollout status deployment/product-service -n modern-upo-sandbox
kubectl rollout status deployment/order-service -n modern-upo-sandbox
kubectl rollout status deployment/notification-service -n modern-upo-sandbox
kubectl rollout status deployment/gateway -n modern-upo-sandbox
kubectl rollout status deployment/frontend -n modern-upo-sandbox
```

### 5. Verify service and storage status

```bash
kubectl get pods,svc,ingress -n modern-upo-sandbox
kubectl get pvc -n modern-upo-sandbox
kubectl describe ingress modern-upo-sandbox -n modern-upo-sandbox
```

### 6. Run smoke tests

At minimum, validate:

- frontend loads
- login works
- product browsing works
- admin pages load
- order create path works
- order event consumer stays connected

Suggested checks:

- browse the sandbox ingress host
- call `/ready` and `/live` on the gateway
- inspect service docs where exposed
- run the repo smoke scripts against the sandbox endpoint shape if reachable

## Routine Release Flow

Use this flow for a normal application refresh after a validated `sandbox`
branch update.

### 1. Confirm cluster health before rollout

```bash
kubectl get pods -n modern-upo-sandbox
kubectl get events -n modern-upo-sandbox --sort-by=.lastTimestamp
```

### 2. Release the new image set

Choose one path:

- preferred:
  - update manifests in Git and run `kubectl apply -k infra/k8s/sandbox`
- emergency:
  - patch images with `kubectl set image`

### 3. Watch the rollout

```bash
kubectl rollout status deployment/frontend -n modern-upo-sandbox
kubectl rollout status deployment/gateway -n modern-upo-sandbox
kubectl rollout status deployment/user-service -n modern-upo-sandbox
kubectl rollout status deployment/product-service -n modern-upo-sandbox
kubectl rollout status deployment/order-service -n modern-upo-sandbox
kubectl rollout status deployment/notification-service -n modern-upo-sandbox
```

### 4. Re-run smoke tests and dashboards

Confirm:

- ingress is serving the expected frontend
- gateway traffic is healthy
- request failures are not spiking
- RabbitMQ consumer is connected
- MongoDB audit writes still succeed if enabled

## Restart Runbook

Restart should be chosen based on the blast radius of the issue.

### Safe Application Restart

Use for:

- stuck application pod
- transient dependency connection issue
- config change already applied to ConfigMap or Secret

Commands:

```bash
kubectl rollout restart deployment/frontend -n modern-upo-sandbox
kubectl rollout restart deployment/gateway -n modern-upo-sandbox
kubectl rollout restart deployment/user-service -n modern-upo-sandbox
kubectl rollout restart deployment/product-service -n modern-upo-sandbox
kubectl rollout restart deployment/order-service -n modern-upo-sandbox
kubectl rollout restart deployment/notification-service -n modern-upo-sandbox
```

Then wait:

```bash
kubectl rollout status deployment/gateway -n modern-upo-sandbox
kubectl rollout status deployment/order-service -n modern-upo-sandbox
```

### Restart After Config or Secret Change

Use for:

- JWT secret rotation
- MySQL or RabbitMQ credential change
- rate-limit parameter update
- CORS or ingress host config update

Flow:

1. update the Secret or ConfigMap source
2. `kubectl apply -k infra/k8s/sandbox`
3. restart only the affected Deployments
4. verify fresh pods picked up the new values

### Data Service Restart

Use extra caution for:

- MySQL
- Redis
- RabbitMQ
- MongoDB

Commands:

```bash
kubectl rollout restart deployment/mysql -n modern-upo-sandbox
kubectl rollout restart deployment/redis -n modern-upo-sandbox
kubectl rollout restart deployment/rabbitmq -n modern-upo-sandbox
kubectl rollout restart deployment/mongodb -n modern-upo-sandbox
```

Notes:

- these workloads have PVCs in the current baseline, so routine pod restart
  should not wipe data
- do not delete PVCs as part of restart work
- restart one data component at a time unless the incident clearly requires a
  broader recovery
- restart dependent application services afterward if they do not reconnect
  cleanly

### What Not To Do For Routine Work

- do not delete the entire namespace to "restart everything"
- do not delete PVCs unless you explicitly want data loss
- do not restart all data services and all applications at once without a clear
  incident reason

## Rollback Runbook

### Application Rollback

Use when a fresh rollout introduces regressions but the underlying data plane is
healthy.

```bash
kubectl rollout undo deployment/frontend -n modern-upo-sandbox
kubectl rollout undo deployment/gateway -n modern-upo-sandbox
kubectl rollout undo deployment/user-service -n modern-upo-sandbox
kubectl rollout undo deployment/product-service -n modern-upo-sandbox
kubectl rollout undo deployment/order-service -n modern-upo-sandbox
kubectl rollout undo deployment/notification-service -n modern-upo-sandbox
```

Then verify:

```bash
kubectl rollout status deployment/gateway -n modern-upo-sandbox
kubectl rollout history deployment/gateway -n modern-upo-sandbox
```

If the cluster was changed declaratively through Git, follow up by restoring the
manifest state in the repository and re-applying it so the cluster and Git stay
aligned.

### Config Rollback

Use when the issue is caused by ConfigMap or Secret content, not image code.

Flow:

1. restore the last known good config source
2. `kubectl apply -k infra/k8s/sandbox`
3. restart the affected Deployments
4. re-run smoke tests

### Data Rollback

For MySQL, Redis, RabbitMQ, and MongoDB, rollback usually means restoring from
backup or recovering the workload, not simply undoing a Deployment revision.

Treat these as a separate incident workflow:

- confirm current data safety
- stop making the situation worse
- restore from backup only with explicit intent
- restart application services after the dependency is healthy again

## Health Checks And Triage

### Quick Cluster View

```bash
kubectl get pods -n modern-upo-sandbox -o wide
kubectl get svc -n modern-upo-sandbox
kubectl get ingress -n modern-upo-sandbox
kubectl get pvc -n modern-upo-sandbox
kubectl get events -n modern-upo-sandbox --sort-by=.lastTimestamp
```

### Pod-Level Inspection

```bash
kubectl describe pod <pod-name> -n modern-upo-sandbox
kubectl logs deployment/gateway -n modern-upo-sandbox --tail=200
kubectl logs deployment/order-service -n modern-upo-sandbox --tail=200
kubectl logs deployment/notification-service -n modern-upo-sandbox --tail=200
kubectl logs deployment/rabbitmq -n modern-upo-sandbox --tail=200
```

### Service Reachability Checks

```bash
kubectl port-forward svc/gateway 8000:8000 -n modern-upo-sandbox
kubectl port-forward svc/user-service 8001:8001 -n modern-upo-sandbox
kubectl port-forward svc/product-service 8002:8002 -n modern-upo-sandbox
kubectl port-forward svc/order-service 8080:8080 -n modern-upo-sandbox
```

Then test locally:

```bash
curl -i http://127.0.0.1:8000/ready
curl -i http://127.0.0.1:8000/live
curl -i http://127.0.0.1:8001/ready
curl -i http://127.0.0.1:8002/ready
curl -i http://127.0.0.1:8080/ready
```

## Common Recovery Patterns

### Gateway Is Up But Login Fails

Check:

- `user-service` pod health
- MySQL connectivity from `user-service`
- JWT secret alignment between `gateway` and `user-service`
- Redis availability if blacklist logic is enabled

### Product Pages Load Slowly Or Fail Intermittently

Check:

- `product-service` readiness
- Redis health and reconnect status
- gateway logs for upstream timeout patterns

### Order Creation Fails

Check:

- `order-service` readiness
- MySQL connectivity for `h_order_db`
- `product-service` internal call path
- RabbitMQ connectivity if the error happens after order write

### Order Events Stop Flowing To Notification Logs

Check:

- RabbitMQ pod health
- `notification-service` logs
- `order-service` logs for outbox relay errors
- MongoDB connectivity only if audit persistence is enabled

## Suggested Manual Validation Checklist

After startup, restart, or rollback:

- sign in as `john_smith`
- browse products
- sign in as `admin`
- open product admin page
- open admin orders page
- create an order as a demo user
- approve or reject an order as admin
- confirm notification-side processing remains healthy

## Recommended Next Automation

The repository is ready for a future deployment workflow that does the
following:

1. build and tag all sandbox images from the `sandbox` branch
2. push them to the registry
3. update Kubernetes image references
4. apply `infra/k8s/sandbox`
5. wait for rollouts
6. run smoke tests
7. report status back into CI

Until that pipeline exists, the commands in this runbook are the recommended
manual operating procedure.
