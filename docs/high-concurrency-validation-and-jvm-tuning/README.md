# Batch 7: High-Concurrency Validation and JVM Tuning

This folder contains independent operation documents for Batch 7.
Each document can be reviewed and executed separately.

## Scope

- Service focus: `order-service`
- Goal: validate concurrency behavior and define practical JVM tuning actions
- Environment: sandbox first, then EC2 online path after sandbox sign-off

## Execution Order

0. `batch-7-execution-sop-for-pressure-team.md` (must-read handoff guide)
1. `batch-7.1-baseline-and-observability.md`
2. `batch-7.2-checkout-concurrency-validation.md`
3. `batch-7.3-jvm-tuning-and-diagnostics.md`
4. `batch-7.4-acceptance-checklist-and-report.md`
5. `batch-7-open-items-and-resume-plan.md` (current unfinished status + resume guide)

## Notes

- Batch 7.1 is intentionally no business-code change.
- Keep all records (command logs, screenshots, metric snapshots) for PR review.

## Current Progress Snapshot (as of 2026-04-24)

- `7.1`: completed with baseline evidence.
- `7.2`: functional/data-integrity issues resolved; latency threshold still requires 7.3 optimization closure.
- `7.3`: in progress through `7.3.9.1`; hotspot identified on product stock row update lock time.
- `7.4`: not started yet (waiting 7.3 close or blocker sign-off path).

For restart/handoff details, use:

- `batch-7-open-items-and-resume-plan.md`
