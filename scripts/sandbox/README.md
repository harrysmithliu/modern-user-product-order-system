# Sandbox Scripts

Utilities for the `sandbox -> sandbox-ec2-online` promotion path.

## 1. Start or refresh the sandbox stack

```bash
docker compose --env-file infra/docker/.env.sandbox.example -f infra/docker/docker-compose.sandbox.yml up -d --build
```

## 2. Run the sandbox smoke tests

Phase 1 business flow:

```bash
python3 scripts/sandbox/smoke-test-phase1.py
```

Phase 2 Redis flow:

```bash
python3 scripts/sandbox/smoke-test-phase2.py
```

RabbitMQ event flow:

```bash
SANDBOX_PRODUCT_SERVICE_URL=http://127.0.0.1:8002 \
SANDBOX_ORDER_SERVICE_URL=http://127.0.0.1:8080 \
python3 scripts/sandbox/smoke-test-rabbitmq.py
```

The RabbitMQ smoke test is optional, but it is the right check when the batch
touches order events, notification delivery, or direct service-to-service event
flow.

MongoDB audit flow:

```bash
python3 scripts/sandbox/smoke-test-mongodb.py
```

The MongoDB smoke test is optional, but it is the right check when the batch
touches notification audit persistence or side-channel audit lookup.
It uses the sandbox Mongo connection settings from
`infra/docker/.env.sandbox.example`, so the local sandbox stack must be up
before you run it.

## 3. Sync shared sandbox changes into EC2 online

After the sandbox batch is validated, selectively promote the shared files to
`sandbox-ec2-online`:

```bash
bash scripts/sandbox/sync-sandbox-to-ec2online.sh
```

This helper keeps the following paths out of the EC2 demo sync:

- `infra/aws/prod/**`
- `infra/k8s/prod/**`
- `infra/aws/sandbox-ec2/**`

It is intended for the release path where shared sandbox changes are promoted to
the long-running EC2 demo line without overwriting EC2-specific deployment
files.
