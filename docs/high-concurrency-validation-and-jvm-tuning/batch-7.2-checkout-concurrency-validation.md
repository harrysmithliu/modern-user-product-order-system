# Batch 7.2 - Checkout Concurrency Validation

## Objective

Validate concurrency behavior of checkout-related flows in `order-service`, with focus on:

- in-flight lock correctness for the same `order_id`
- parallel throughput for different `order_id` values
- external call guard behavior under pressure

## Recommended Test Matrix

1. Same user, same order, repeated pay retries
2. Many users, different orders, concurrent pay requests
3. Mixed success/failure pressure (payment timeout simulation + coupon issue failure simulation)

## Suggested Load Profile

- Warm-up: low traffic for 2-5 minutes
- Main run: gradually ramp to the target concurrency
- Soak: keep stable load for 10-30 minutes
- Cool-down: gradual decrease

For a 10,000-user target, run staged scaling (for example: 500 -> 2,000 -> 5,000 -> 10,000 logical users) instead of one-shot full load.

## What to Observe

1. API metrics:
   - `POST /api/orders`
   - `POST /api/orders/{id}/pay`
   - `POST /api/admin/orders/{id}/approve`
   - `POST /api/admin/orders/{id}/reject`
2. Error distribution:
   - `409` (conflict/concurrency)
   - `429` (rate limit)
   - `504` (simulated payment timeout)
   - `5xx` (unexpected internal failures)
3. Internal behavior:
   - workflow executor queue pressure
   - semaphore saturation and wait time
   - lock contention for same order ids

## Data Checks in Database

- No duplicate order records for the same `user_id + request_no`
- Valid status transitions only
- No stuck intermediate statuses beyond acceptable retry window
- Retry tasks (`t_order_coupon_issue_task`) progress as expected

## Exit Criteria

- Throughput and latency remain within agreed threshold for each stage
- Failure patterns match expected simulation behavior
- No data integrity anomalies are found
