#!/usr/bin/env bash

set -euo pipefail

required_vars=(
  AWS_ACCOUNT_ID
  AWS_REGION
  AWS_PROFILE
  IMAGE_TAG
  ECR_FRONTEND_REPO
  ECR_GATEWAY_REPO
  ECR_USER_SERVICE_REPO
  ECR_PRODUCT_SERVICE_REPO
  ECR_ORDER_SERVICE_REPO
  ECR_NOTIFICATION_SERVICE_REPO
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "Missing required environment variable: ${var}" >&2
    exit 1
  fi
done

command -v aws >/dev/null 2>&1 || { echo "aws CLI is required"; exit 1; }
command -v docker >/dev/null 2>&1 || { echo "docker is required"; exit 1; }

ACCOUNT_REGISTRY="${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com"

declare -a image_map=(
  "upo-frontend:prod:${ECR_FRONTEND_REPO}"
  "upo-gateway:prod:${ECR_GATEWAY_REPO}"
  "upo-user-service:prod:${ECR_USER_SERVICE_REPO}"
  "upo-product-service:prod:${ECR_PRODUCT_SERVICE_REPO}"
  "upo-order-service:prod:${ECR_ORDER_SERVICE_REPO}"
  "upo-notification-service:prod:${ECR_NOTIFICATION_SERVICE_REPO}"
)

echo "Checking local source images..."
for mapping in "${image_map[@]}"; do
  IFS=":" read -r local_image local_tag repo_name <<<"${mapping}"
  docker image inspect "${local_image}:${local_tag}" >/dev/null 2>&1 || {
    echo "Missing local image: ${local_image}:${local_tag}" >&2
    exit 1
  }
done

echo "Logging in to ECR..."
aws --profile "${AWS_PROFILE}" --region "${AWS_REGION}" ecr get-login-password \
  | docker login --username AWS --password-stdin "${ACCOUNT_REGISTRY}"

for mapping in "${image_map[@]}"; do
  IFS=":" read -r local_image local_tag repo_name <<<"${mapping}"
  remote_image="${ACCOUNT_REGISTRY}/${repo_name}:${IMAGE_TAG}"

  echo "Ensuring ECR repository exists: ${repo_name}"
  aws --profile "${AWS_PROFILE}" --region "${AWS_REGION}" ecr describe-repositories \
    --repository-names "${repo_name}" >/dev/null 2>&1 \
    || aws --profile "${AWS_PROFILE}" --region "${AWS_REGION}" ecr create-repository \
      --repository-name "${repo_name}" >/dev/null

  echo "Tagging ${local_image}:${local_tag} -> ${remote_image}"
  docker tag "${local_image}:${local_tag}" "${remote_image}"

  echo "Pushing ${remote_image}"
  docker push "${remote_image}"
done

echo "ECR push complete."
