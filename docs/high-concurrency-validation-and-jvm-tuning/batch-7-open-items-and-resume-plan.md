# Batch 7 Open Items and Resume Plan

## Purpose

This file is the handoff anchor for unfinished Batch 7 work.
Use it to resume execution without re-discovery.

## Snapshot

- Snapshot date: `2026-04-24` (America/Toronto)
- Working branch during latest run: `sandbox-batch7-baseline`
- Latest related commits:
  - `5d99149` (`7.3.9.1 rollback baseline + DB hotspot evidence`)
  - `540f607` (`7.3.8 trial evidence`)
  - `51bd364` (`7.3.7 shaping rerun`)

## What Is Done

1. `7.1` completed
- Evidence: `scripts/test/7-1/2026-04-20-7.1-summary.md`

2. `7.2` completed (correctness/integrity lane)
- Evidence: `scripts/test/7-2/2026-04-23-7.2-stockfix-summary.md`
- Conclusion: functional path stable; remaining blocker moved to latency/performance.

3. `7.3` partially completed (through `7.3.9.1`)
- Latest evidence: `scripts/test/7-3/2026-04-24-7.3.9-summary.md`
- Current best diagnosis: hotspot contention around:
  - `h_product_db.t_product` stock update row lock accumulation.

## What Is Not Done

1. `7.3` is not closed yet
- Reason: latency thresholds still failing.
- Latest threshold state (`7.3.9.1`):
  - `http_req_duration p95<3000`: fail
  - `create p95<2000`: fail
  - `pay p95<2500`: fail

2. `7.4` acceptance report not generated
- Reason: depends on final `7.3` closure or formal blocker sign-off.

## Resume Task List (Next Operator)

1. Run `7.3.9.2` multi-product sharding validation
- Goal: verify if p95 tail is reduced when single-row stock hotspot is diluted.
- Action:
  - extend or fork k6 scenario under `scripts/load/` to distribute create orders across multiple product IDs.
  - keep the same stage profile first (500 VU, 20s/40s/10s) for comparability.
  - output to `scripts/test/7-3/` with date + `7.3.9.2-*` naming.

2. If `7.3.9.2` improves significantly, run one confirmation rerun (`7.3.9.3`)
- Goal: check reproducibility.

3. Close `7.3` with one final summary
- Include side-by-side comparison:
  - `7.3.7`, `7.3.8`, `7.3.9.1`, and new runs.

4. Start `7.4` acceptance document
- Fill checklist and release-facing report using `batch-7.4-acceptance-checklist-and-report.md`.
- Create `scripts/test/7-4/<date>-7.4-summary.md`.

## Required Evidence on Resume

For each new `7.3.x` run:

- command log (`*-command-log.txt`)
- k6 summary (`*-summary.json`) and raw log (`*.log`)
- metrics table (`*-metrics.csv`)
- stage conclusion (`*-summary.md`)
- gateway/order/product logs post-run
- DB integrity checks and stock snapshots
- limiter temp/restore proof files

## Stop Rules

Stop and open blocker if any occurs:

- data integrity corruption (duplicate request keys or invalid order transitions)
- sustained high 5xx over agreed threshold window
- non-recoverable dependency instability in sandbox runtime

## Done Definition for Batch 7

Batch 7 can be considered complete only when both are true:

1. `7.3` is closed (threshold pass or explicitly approved blocker conclusion).
2. `7.4` checklist + final report are fully documented and reproducible.
