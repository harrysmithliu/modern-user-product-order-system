#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../../.." && pwd)"
AWS_DIR="${ROOT_DIR}/infra/aws/sandbox-ec2"
ENV_FILE="${1:-${AWS_DIR}/.env.ec2.local}"
ACTIVE_NGINX_CONF="${AWS_DIR}/nginx/active.conf"
RUNTIME_DIR="${AWS_DIR}/runtime"
COMPOSE_FILE="${AWS_DIR}/docker-compose.yml"

COMPOSE_ARGS=(
  --env-file "${ENV_FILE}"
  -f "${COMPOSE_FILE}"
)

APP_SERVICES=(
  frontend
  gateway
  user-service
  product-service
  order-service
  notification-service
)

EDGE_SERVICES=(
  reverse-proxy
)

if [[ ! -f "${ENV_FILE}" ]]; then
  echo "Missing env file: ${ENV_FILE}"
  exit 1
fi

set -a
source "${ENV_FILE}"
set +a

if [[ -z "${APP_DOMAIN:-}" ]]; then
  echo "APP_DOMAIN is required."
  exit 1
fi

mkdir -p "${RUNTIME_DIR}/certbot/www" "${RUNTIME_DIR}/letsencrypt"

HTTPS_CERT_DIR="${RUNTIME_DIR}/letsencrypt/live/${APP_DOMAIN}"
if [[ -f "${HTTPS_CERT_DIR}/fullchain.pem" && -f "${HTTPS_CERT_DIR}/privkey.pem" ]]; then
  envsubst '${APP_DOMAIN}' <"${AWS_DIR}/nginx/default.https.conf" >"${ACTIVE_NGINX_CONF}"
else
  cp "${AWS_DIR}/nginx/default.http.conf" "${ACTIVE_NGINX_CONF}"
fi

for service in "${APP_SERVICES[@]}"; do
  echo "Building ${service}..."
  COMPOSE_BAKE=false docker compose "${COMPOSE_ARGS[@]}" build "${service}"
done

echo "Starting containers..."
docker compose "${COMPOSE_ARGS[@]}" up -d

for service in "${EDGE_SERVICES[@]}"; do
  echo "Refreshing ${service}..."
  docker compose "${COMPOSE_ARGS[@]}" up -d --force-recreate "${service}"
done

echo "Deployment applied."
