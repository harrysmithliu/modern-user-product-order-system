# Load Test Scripts

This directory contains Batch D starter load-test scripts based on `k6`.

## Included

- `k6-products.js`
  - product listing read-path load
- `k6-orders.js`
  - authenticated order creation flow load

## Recommended Targets

- sandbox gateway:
  - `http://localhost:8000`
- frontend demo host:
  - `http://localhost:5173`

## Example

```bash
k6 run scripts/load/k6-products.js
k6 run scripts/load/k6-orders.js
```

Docker alternative:

```bash
docker run --rm -i -v "$PWD/scripts/load:/scripts" grafana/k6 run -e BASE_URL=http://host.docker.internal:8000 /scripts/k6-products.js
docker run --rm -i -v "$PWD/scripts/load:/scripts" grafana/k6 run -e BASE_URL=http://host.docker.internal:8000 -e PRODUCT_ID=1 /scripts/k6-orders.js
```

## Environment Variables

- `BASE_URL`
  - defaults to `http://localhost:8000`
- `USERNAME`
  - defaults to `john_smith`
- `PASSWORD`
  - defaults to `User@123`
- `PRODUCT_ID`
  - defaults to `1`
- `VUS`
  - optional override for the order scenario virtual users
- `DURATION`
  - optional override for the order scenario duration
- `SLEEP_SECONDS`
  - optional override for pacing between authenticated order requests

## Notes

- the default order scenario is intentionally conservative so it stays inside the gateway order-create rate limit and reflects a protected write-path baseline
- if you increase `VUS` or remove pacing, expect `429` responses from the gateway once the configured limiter is exceeded
