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

## Notes

- the product script exercises the cache-friendly read path
- the order script performs login plus order creation
- the order script uses unique `request_no` values so idempotency keys do not collide
- the current baseline is intended for local or sandbox demonstrations, not formal benchmark claims

## Expected Next Iteration

- persist and compare k6 summary output
- add a scenario for admin approval throughput
- correlate load runs with future Prometheus / Grafana dashboards
