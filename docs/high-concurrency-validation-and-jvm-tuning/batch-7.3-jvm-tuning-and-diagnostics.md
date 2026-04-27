# Batch 7.3 - JVM Tuning and Diagnostics

## Objective

Tune JVM runtime behavior for the checkout-heavy workload and provide a repeatable diagnostics workflow.

## Current Execution Status (2026-04-24 Snapshot)

- Stage status: `IN PROGRESS` (executed to `7.3.9.1`, not closed).
- Latest run summary: `scripts/test/7-3/2026-04-24-7.3.9-summary.md`
- Current conclusion:
  - stability is acceptable (`0%` failed requests in latest lane),
  - latency thresholds are still not met,
  - dominant hotspot evidence points to stock row lock contention on product update SQL.
- Resume reference: `batch-7-open-items-and-resume-plan.md`

## Baseline JVM Focus Areas

1. Heap sizing (`-Xms`, `-Xmx`)
2. GC strategy and pause target
3. Metaspace boundaries
4. Thread and memory diagnostics under load

## Recommended Initial Baseline (Example)

- `-Xms4g -Xmx4g`
- `-XX:+UseG1GC`
- `-XX:MaxGCPauseMillis=200`
- `-XX:MetaspaceSize=256m`
- `-XX:MaxMetaspaceSize=512m`
- `-XX:+HeapDumpOnOutOfMemoryError`

Adjust values according to EC2 instance limits and total pod/process memory budget.

## Diagnostics Commands

```bash
jstat -gcutil <pid> 1000
jstack <pid>
jmap -histo <pid> | head -n 20
```

Use these snapshots during load and immediately after peak windows.

## Incident Workflow (When Full GC or OOM Happens)

1. Capture process state (`jstat`, `jstack`, `jmap -histo`)
2. Export heap dump (`-XX:+HeapDumpOnOutOfMemoryError` or manual `jmap -dump`)
3. Analyze with MAT/JProfiler
4. Identify root cause category:
   - long-lived object accumulation
   - cache growth without bound
   - retry/task backlog retention
   - thread/queue amplification
5. Apply code/config fix and re-run Batch 7.2 test stages

## Exit Criteria

- GC pause behavior remains stable under target load stages
- No repeated Full GC storm pattern in soak run
- No memory growth trend indicating leak risk
