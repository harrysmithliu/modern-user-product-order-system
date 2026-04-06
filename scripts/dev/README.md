# Dev Notes

Recommended startup order for the current Phase 1 batch:

1. `services/user-service`
2. `services/product-service`
3. `services/order-service`
4. `gateway`
5. `frontend`

Suggested smoke checks after startup:

1. `POST /api/auth/login`
2. `GET /api/products?page=1&size=5`
3. `POST /api/orders`
4. `GET /api/orders/my`
5. `GET /api/admin/orders`

Automated Phase 1 smoke test:

```bash
python3 scripts/dev/smoke-test-phase1.py
```

Automated Phase 2 Redis smoke test:

```bash
python3 scripts/dev/smoke-test-phase2.py
```

Automated dev RabbitMQ smoke test:

```bash
python3 scripts/dev/smoke-test-rabbitmq-dev.py
```

Automated local kind validation for the sandbox Kubernetes baseline:

```bash
bash scripts/dev/validate-k8s-sandbox-kind.sh
```

Python unit tests:

```bash
bash scripts/dev/run-python-unit-tests.sh
```

RabbitMQ event development should be validated in the local `dev` runtime first:

```bash
docker compose --env-file infra/docker/.env.dev.example -f infra/docker/docker-compose.dev.yml up -d --build
```

The `dev` stack is allowed to reuse host-managed infrastructure, including local containers such as `local-mysql` and `rmq`.

For RabbitMQ event work, inspect the local notification worker after you trigger order actions:

```bash
docker logs --tail 50 modern-upo-dev-notification-service
```

Optional base URL override:

```bash
SMOKE_TEST_BASE_URL=http://127.0.0.1:8010 python3 scripts/dev/smoke-test-phase1.py
```

```bash
SMOKE_TEST_BASE_URL=http://127.0.0.1:8010 python3 scripts/dev/smoke-test-phase2.py
```

```bash
DEV_PRODUCT_SERVICE_URL=http://127.0.0.1:8002 DEV_ORDER_SERVICE_URL=http://127.0.0.1:8080 python3 scripts/dev/smoke-test-rabbitmq-dev.py
```

The script creates real sample orders in the local development database so the review and history pages have data to display.

The script assumes these services are already running locally:

1. `user-service` on `127.0.0.1:8001`
2. `product-service` on `127.0.0.1:8002`
3. `order-service` on `127.0.0.1:8080`
4. `gateway` on `127.0.0.1:8000`

The smoke test covers:

1. user login
2. admin login
3. product listing
4. create + cancel flow
5. create + approve flow
6. create + reject flow
7. latest-first order sorting
8. pending queue verification

The Redis smoke test covers:

1. user login
2. authenticated profile lookup
3. logout
4. revoked token rejection
5. re-login after logout
6. product listing with the fresh token

The dev RabbitMQ smoke test covers:

1. local product listing against `product-service`
2. create + cancel flow directly against `order-service`
3. create + approve flow directly against `order-service`
4. create + reject flow directly against `order-service`
5. printed order numbers for checking the `notification-service` IDE console logs

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
