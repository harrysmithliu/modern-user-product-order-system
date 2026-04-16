#!/usr/bin/env bash

set -euo pipefail

required_vars=(
  AWS_REGION
  AWS_PROFILE
  EKS_CLUSTER_NAME
  EKS_NAMESPACE
  JWT_SECRET
  MYSQL_APP_USER
  MYSQL_APP_PASSWORD
  MYSQL_ROOT_PASSWORD
  RABBITMQ_DEFAULT_USER
  RABBITMQ_DEFAULT_PASS
  MONGO_INITDB_ROOT_USERNAME
  MONGO_INITDB_ROOT_PASSWORD
)

for var in "${required_vars[@]}"; do
  if [[ -z "${!var:-}" ]]; then
    echo "Missing required environment variable: ${var}" >&2
    exit 1
  fi
done

command -v aws >/dev/null 2>&1 || { echo "aws CLI is required"; exit 1; }
command -v kubectl >/dev/null 2>&1 || { echo "kubectl is required"; exit 1; }

placeholder_markers=(
  change-me
  change-me-in-env
  change-me-in-production
  app_pass
  root_pass
  admin123
  REPLACE_BEFORE_DEPLOY
  REPLACE_IN_SECRET_MANAGER
)

is_placeholder_value() {
  local value="$1"
  for marker in "${placeholder_markers[@]}"; do
    if [[ "${value}" == "${marker}" || "${value}" == *"${marker}"* ]]; then
      return 0
    fi
  done
  return 1
}

notification_service_mongo_uri="${NOTIFICATION_SERVICE_MONGO_URI:-}"
if [[ -z "${notification_service_mongo_uri}" ]]; then
  mongo_host="${MONGO_HOST:-${MONGODB_HOST:-docdb.cluster-xxxxxxxxxx.us-east-1.docdb.amazonaws.com}}"
  mongo_port="${MONGO_PORT:-27017}"
  mongo_auth_db="${MONGO_AUTH_DB:-admin}"
  mongo_uri_options="${MONGO_URI_OPTIONS:-authSource=admin&retryWrites=false}"
  notification_service_mongo_uri="mongodb://${MONGO_INITDB_ROOT_USERNAME}:${MONGO_INITDB_ROOT_PASSWORD}@${mongo_host}:${mongo_port}/${mongo_auth_db}?${mongo_uri_options}"
fi

guarded_secret_vars=(
  JWT_SECRET
  MYSQL_APP_PASSWORD
  MYSQL_ROOT_PASSWORD
  RABBITMQ_DEFAULT_PASS
  MONGO_INITDB_ROOT_PASSWORD
)

preflight_failed=0
for var in "${guarded_secret_vars[@]}"; do
  if is_placeholder_value "${!var}"; then
    echo "Refusing deployment: ${var} still contains a placeholder/default value." >&2
    preflight_failed=1
  fi
done

if is_placeholder_value "${notification_service_mongo_uri}"; then
  echo "Refusing deployment: NOTIFICATION_SERVICE_MONGO_URI resolved to a placeholder/default value." >&2
  preflight_failed=1
fi

if [[ "${preflight_failed}" -ne 0 ]]; then
  echo "Set real production secret values before running deploy-eks.sh." >&2
  exit 1
fi

echo "Updating kubeconfig for cluster ${EKS_CLUSTER_NAME}"
aws --profile "${AWS_PROFILE}" --region "${AWS_REGION}" eks update-kubeconfig \
  --name "${EKS_CLUSTER_NAME}"

echo "Verifying cluster connectivity"
kubectl cluster-info

echo "Ensuring namespace ${EKS_NAMESPACE}"
kubectl get namespace "${EKS_NAMESPACE}" >/dev/null 2>&1 \
  || kubectl create namespace "${EKS_NAMESPACE}"

echo "Applying prod-app-secret (manifest-compatible)"
kubectl create secret generic prod-app-secret \
  --namespace "${EKS_NAMESPACE}" \
  --from-literal=mysql-app-user="${MYSQL_APP_USER}" \
  --from-literal=mysql-app-password="${MYSQL_APP_PASSWORD}" \
  --from-literal=jwt-secret="${JWT_SECRET}" \
  --from-literal=rabbitmq-username="${RABBITMQ_DEFAULT_USER}" \
  --from-literal=rabbitmq-password="${RABBITMQ_DEFAULT_PASS}" \
  --from-literal=notification-service-mongo-uri="${notification_service_mongo_uri}" \
  --dry-run=client -o yaml | kubectl apply -f -

echo "Applying modern-upo-prod-secret (legacy compatibility)"
kubectl create secret generic modern-upo-prod-secret \
  --namespace "${EKS_NAMESPACE}" \
  --from-literal=JWT_SECRET="${JWT_SECRET}" \
  --from-literal=MYSQL_APP_USER="${MYSQL_APP_USER}" \
  --from-literal=MYSQL_APP_PASSWORD="${MYSQL_APP_PASSWORD}" \
  --from-literal=MYSQL_ROOT_PASSWORD="${MYSQL_ROOT_PASSWORD}" \
  --from-literal=RABBITMQ_DEFAULT_USER="${RABBITMQ_DEFAULT_USER}" \
  --from-literal=RABBITMQ_DEFAULT_PASS="${RABBITMQ_DEFAULT_PASS}" \
  --from-literal=MONGO_INITDB_ROOT_USERNAME="${MONGO_INITDB_ROOT_USERNAME}" \
  --from-literal=MONGO_INITDB_ROOT_PASSWORD="${MONGO_INITDB_ROOT_PASSWORD}" \
  --dry-run=client -o yaml | kubectl apply -f -

if [[ -f "infra/k8s/prod/kustomization.yaml" ]]; then
  echo "Applying infra/k8s/prod"
  kubectl apply -k infra/k8s/prod
else
  echo "No infra/k8s/prod/kustomization.yaml found yet."
  echo "Create the production manifest set before using this script for a full rollout."
fi

echo "EKS deployment baseline complete."
