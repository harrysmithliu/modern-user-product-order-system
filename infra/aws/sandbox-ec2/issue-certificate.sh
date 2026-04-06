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

if [[ -z "${APP_DOMAIN:-}" || -z "${LETSENCRYPT_EMAIL:-}" ]]; then
  echo "APP_DOMAIN and LETSENCRYPT_EMAIL are required."
  exit 1
fi

docker compose \
  --env-file "${ENV_FILE}" \
  -f "${AWS_DIR}/docker-compose.yml" \
  run --rm certbot certonly \
  --webroot \
  --webroot-path /var/www/certbot \
  --email "${LETSENCRYPT_EMAIL}" \
  --agree-tos \
  --no-eff-email \
  -d "${APP_DOMAIN}"

bash "${AWS_DIR}/deploy.sh" "${ENV_FILE}"

docker compose \
  --env-file "${ENV_FILE}" \
  -f "${AWS_DIR}/docker-compose.yml" \
  exec reverse-proxy nginx -s reload

echo "TLS certificate issued and Nginx reloaded."
