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

## Environment Variables

- `BASE_URL`
  - defaults to `http://localhost:8000`
- `USERNAME`
  - defaults to `john_smith`
- `PASSWORD`
  - defaults to `User@123`
- `PRODUCT_ID`
  - defaults to `1`
