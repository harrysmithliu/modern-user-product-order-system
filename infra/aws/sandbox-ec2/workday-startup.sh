#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
AWS_DIR="${ROOT_DIR}/infra/aws/sandbox-ec2"
ENV_FILE="${1:-${AWS_DIR}/.env.ec2.local}"

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing env file: ${ENV_FILE}"
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

echo "[startup] Deploying stack..."
bash "${AWS_DIR}/deploy.sh" "${ENV_FILE}"

if [[ -n "${ROUTE53_HOSTED_ZONE_ID:-}" ]]; then
  echo "[startup] Syncing Route53 A record..."
  bash "${AWS_DIR}/sync-route53-record.sh" "${ENV_FILE}"
else
  echo "[startup] ROUTE53_HOSTED_ZONE_ID is not configured, skipping DNS sync."
fi

if [[ "${AUTO_CERTBOT_ON_STARTUP:-false}" == "true" ]]; then
  echo "[startup] Running certbot keep-until-expiring check..."
  bash "${AWS_DIR}/issue-certificate.sh" "${ENV_FILE}"
else
  echo "[startup] AUTO_CERTBOT_ON_STARTUP is false, skipping certbot check."
fi

echo "[startup] Done."
