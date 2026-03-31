#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/../.." && pwd)"

(
  cd "$ROOT_DIR/gateway"
  .venv/bin/python -m pytest tests "$@"
)

(
  cd "$ROOT_DIR/services/user-service"
  .venv/bin/python -m pytest tests "$@"
)

(
  cd "$ROOT_DIR/services/product-service"
  .venv/bin/python -m pytest tests "$@"
)

(
  cd "$ROOT_DIR/services/notification-service"
  .venv/bin/python -m pytest tests "$@"
)
