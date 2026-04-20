# Batch 7.1 - Baseline and Observability Freeze (No Business-Code Change)

## Objective

Freeze a stable measurement baseline before any Batch 7 tuning actions.

## Inputs

- Current branch commit SHA
- Current runtime env for `order-service`
- Current scheduler and workflow settings from:
  - `services/order-service/.env.example`
  - `services/order-service/src/main/resources/application.yml`

## Steps

1. Record baseline commit and deployment target.
2. Confirm key workflow settings:
   - executor pool sizes
   - external call semaphore permits
   - coupon retry parameters
   - auto-complete main cron and compensation delay
3. Verify actuator and metrics exposure:
   - `/actuator/health`
   - `/actuator/prometheus` (if enabled in the running environment)
4. Capture current order flow behavior with small sample requests:
   - create order
   - pay order
   - admin approve
   - admin reject
5. Capture a baseline snapshot:
   - API latency summary (avg/p95)
   - error rate by endpoint
   - order status transition counts

## Deliverables

- Baseline record document (date, commit SHA, env)
- Command history and key logs
- Metrics snapshot images or exported panels

## Exit Criteria

- Team can reproduce baseline data from this document only.
- No functional or schema changes are introduced in this step.
