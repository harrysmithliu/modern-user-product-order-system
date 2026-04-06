# Pressure Test Notes

This document captures the initial Phase 3 load-test baseline for the project.

## Goals

- demonstrate that the product read path can be exercised under concurrent load
- demonstrate that the authenticated order create path can be exercised repeatedly
- provide a portfolio-ready starting point for later benchmarking screenshots and result summaries

## Tooling

- `k6`

## Scripts

- `scripts/load/k6-products.js`
- `scripts/load/k6-orders.js`

## Recommended Sandbox Target

- gateway:
  - `http://localhost:8000`

## Example Commands

```bash
k6 run scripts/load/k6-products.js
k6 run scripts/load/k6-orders.js
```

If `k6` is not installed locally, the same scripts can be executed with Docker:

```bash
docker run --rm -i -v "$PWD/scripts/load:/scripts" grafana/k6 run -e BASE_URL=http://host.docker.internal:8000 /scripts/k6-products.js
docker run --rm -i -v "$PWD/scripts/load:/scripts" grafana/k6 run -e BASE_URL=http://host.docker.internal:8000 -e PRODUCT_ID=1 /scripts/k6-orders.js
```

## Notes

- the product script exercises the cache-friendly read path
- the order script performs one authenticated setup login and then creates orders
- the order script uses unique `request_no` values so idempotency keys do not collide
- the default order scenario is intentionally tuned to stay within the gateway order-create rate limit
- the current baseline is intended for local or sandbox demonstrations, not formal benchmark claims

## Batch D Sandbox Baseline

Environment:
- target gateway: `http://localhost:8000`
- monitoring stack: Prometheus on `http://localhost:9090`, Grafana on `http://localhost:3000`
- Prometheus confirmed sandbox targets up for:
  - gateway `:8000`
  - user-service `:8001`
  - product-service `:8002`
  - order-service `:8080`

### Product Listing Baseline

Scenario:
- script: `scripts/load/k6-products.js`
- 10 VUs for 30s

Observed result:
- requests: `280`
- failure rate: `0.00%`
- average latency: `83.45ms`
- p95 latency: `159.21ms`
- max latency: `398.53ms`

### Order Creation Baseline

Scenario:
- script: `scripts/load/k6-orders.js`
- 1 VU for 20s
- setup login once, then create orders with a 2s pacing interval

Observed result:
- requests: `11`
- failure rate: `0.00%`
- average latency: `83.43ms`
- p95 latency: `245.17ms`
- max latency: `260.84ms`

### Order Rate-Limit Observation

An earlier, more aggressive order scenario with repeated per-iteration login and 5 VUs for 30s crossed the gateway rate-limit policy and produced a high `429` failure rate. That behavior was expected given the current production-style protection rules and is preserved as a useful demonstration that the gateway protects write-heavy endpoints under sustained pressure.

## Expected Next Iteration

- persist and compare k6 summary output
- add a scenario for admin approval throughput
- correlate load runs with future Prometheus / Grafana dashboards
