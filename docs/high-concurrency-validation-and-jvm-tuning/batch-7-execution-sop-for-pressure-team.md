# Batch 7 Execution SOP (Pressure Test Team Handoff)

This SOP is the operational add-on for Batch 7.
It is intended to let the pressure test team execute with minimal back-and-forth.

## 1. Scope and Ownership

- Scope service: `order-service` (plus gateway/user-service/product-service dependency checks)
- Execution environment: sandbox branch and sandbox runtime
- Output owner: pressure test team
- Sign-off owner: backend owner + pressure test owner

## 2. Entry Gate (Must Pass Before Any Run)

Run these checks first:

```bash
curl -sS http://127.0.0.1:8000/ready
curl -sS http://127.0.0.1:8000/live
curl -sS http://127.0.0.1:8080/actuator/health
curl -sS http://127.0.0.1:9090/-/ready
curl -sS http://127.0.0.1:3000/api/health
```

Schema sanity check (avoid login 500 caused by missing user columns):

```sql
SELECT COLUMN_NAME
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA='h_user_db' AND TABLE_NAME='t_user'
  AND COLUMN_NAME IN ('login_enabled');
```

Expected: row exists for `login_enabled`.

## 3. Fixed Test Profiles

Use two explicit profiles:

1. Profile A (baseline stability)
- External simulated platforms disabled (current sandbox default)
- Goal: stable baseline without injected payment/coupon platform failures

2. Profile B (resilience validation)
- Enable payment timeout simulation and coupon issue failure simulation
- Goal: validate retry/error-handling behavior under expected partial failures

Record which profile is used in every result file.

## 4. Load Stages (10k Logical User Path)

Run staged load, do not jump to 10k directly:

1. Stage S1: 500 users
2. Stage S2: 2,000 users
3. Stage S3: 5,000 users
4. Stage S4: 10,000 users

For each stage:

- warm-up: 3 minutes
- steady: 10 minutes
- cool-down: 2 minutes

If a stage fails exit criteria, stop escalation and open a blocker ticket.

## 5. Minimal API Coverage per Stage

- `POST /api/orders`
- `POST /api/orders/{id}/pay`
- `POST /api/admin/orders/{id}/approve`
- `POST /api/admin/orders/{id}/reject`
- `GET /api/orders/my`
- `GET /api/admin/orders`

## 6. Acceptance Thresholds (Default)

Unless a release owner overrides, use:

- Overall 5xx rate: `< 0.5%`
- `POST /api/orders` p95 latency: `< 1200ms`
- `POST /api/orders/{id}/pay` p95 latency: `< 1500ms`
- Admin approve/reject p95 latency: `< 2000ms`
- Data integrity errors: `0` tolerated

Notes:

- `429` may appear under gateway protection stress; treat as capacity signal, not data corruption.
- `504` expectation depends on profile:
  - Profile A: should be near zero
  - Profile B: expected due to simulated timeout logic; verify retry success path

## 7. Observability Evidence (Mandatory)

Collect and attach:

- Prometheus snapshot time window (`http://127.0.0.1:9090`)
- Grafana dashboard screenshots (`http://localhost:3000`)
- JVM/GC diagnostics samples (`jstat`, `jstack`, `jmap -histo`)
- Key service logs (gateway/order-service) for failed windows

Minimum dashboard panels:

- request rate
- p95 latency by endpoint
- HTTP error code distribution
- JVM heap used
- GC pause/collection trend
- executor active threads / queue pressure

## 8. Data Integrity Checks (SQL)

Run after each stage:

```sql
-- duplicate idempotency keys
SELECT user_id, request_no, COUNT(*) cnt
FROM h_order_db.t_order
WHERE request_no IS NOT NULL
GROUP BY user_id, request_no
HAVING cnt > 1;

-- stuck coupon issue tasks
SELECT status, COUNT(*)
FROM h_order_db.t_order_coupon_issue_task
GROUP BY status;

-- delivery completion readiness scan
SELECT COUNT(*) AS due_shipping
FROM h_order_db.t_order
WHERE status = 6 AND expected_delivery_time <= NOW();
```

## 9. Output Files (Naming Convention)

Store all outputs under:

- `scripts/test/<stage>/`

Stage examples:

- `scripts/test/7-1/`
- `scripts/test/7-2/`
- `scripts/test/7-3/`
- `scripts/test/7-4/`

Use naming:

- `<date>-<stage>-summary.md`
- `<date>-<stage>-metrics.csv`
- `<date>-<stage>-grafana-<panel>.png`
- `<date>-<stage>-jvm.txt`

## 10. Stop Conditions

Stop the run and escalate immediately if any occurs:

- data corruption or duplicate order records
- sustained 5xx over threshold for 5+ minutes
- repeated Full GC storm or obvious memory leak trend
- dependency outage (gateway/user/product/order) not recoverable within run window

## 11. Completion Rule

Batch 7 execution is handoff-complete only when:

- all four stages have either pass results or documented blocker evidence
- thresholds are evaluated per stage
- final summary follows `batch-7.4-acceptance-checklist-and-report.md`
