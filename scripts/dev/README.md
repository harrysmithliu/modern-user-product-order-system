# Dev Notes

Automated local kind validation for the sandbox Kubernetes baseline:

```bash
bash scripts/dev/validate-k8s-sandbox-kind.sh
```

Sandbox promotion smoke tests now live under `scripts/sandbox/README.md`.

Python unit tests:

```bash
bash scripts/dev/run-python-unit-tests.sh
```

RabbitMQ event development should be validated in the local `dev` runtime first:

```bash
docker compose --env-file infra/docker/.env.dev.example -f infra/docker/docker-compose.dev.yml up -d --build
```

The `dev` stack is allowed to reuse host-managed infrastructure, including local containers such as `local-mysql`, `rmq`, `local-mongodb`, and `local-redis`.

For RabbitMQ event work, inspect the local notification worker after you trigger order actions:

```bash
docker logs --tail 50 modern-upo-dev-notification-service
```

Optional base URL override:

The local kind validation script covers:

1. creating the `kind-modern-upo` cluster if missing
2. loading the local `upo-*:sandbox` images into the cluster
3. applying `infra/k8s/sandbox`
4. waiting for the 10 core sandbox Deployments to become ready
5. printing pods, services, and ingress status
6. optionally port-forwarding the gateway when `PORT_FORWARD_GATEWAY=true`

The Python unit test batch covers:

1. gateway Redis rate-limit helper behavior
2. gateway route-level rate-limit decisions
3. user-service token creation and blacklist-aware auth helpers
4. product-service catalog cache versioning and cache payload writes
5. notification-service event log parsing for both snake_case and camelCase payloads
