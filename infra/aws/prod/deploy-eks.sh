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

echo "Updating kubeconfig for cluster ${EKS_CLUSTER_NAME}"
aws --profile "${AWS_PROFILE}" --region "${AWS_REGION}" eks update-kubeconfig \
  --name "${EKS_CLUSTER_NAME}"

echo "Verifying cluster connectivity"
kubectl cluster-info

echo "Ensuring namespace ${EKS_NAMESPACE}"
kubectl get namespace "${EKS_NAMESPACE}" >/dev/null 2>&1 \
  || kubectl create namespace "${EKS_NAMESPACE}"

echo "Applying secret baseline"
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
