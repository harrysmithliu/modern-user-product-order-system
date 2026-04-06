#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
CLUSTER_NAME="${KIND_CLUSTER_NAME:-modern-upo}"
KUBECTL_CONTEXT="kind-${CLUSTER_NAME}"
NAMESPACE="${K8S_SANDBOX_NAMESPACE:-modern-upo-sandbox}"
CREATE_CLUSTER_IF_MISSING="${CREATE_KIND_CLUSTER_IF_MISSING:-true}"
LOAD_SANDBOX_IMAGES="${LOAD_SANDBOX_IMAGES:-true}"
ROLLOUT_TIMEOUT="${ROLLOUT_TIMEOUT:-180s}"
PORT_FORWARD_GATEWAY="${PORT_FORWARD_GATEWAY:-false}"
PORT_FORWARD_PORT="${PORT_FORWARD_PORT:-18000}"

REQUIRED_IMAGES=(
  "upo-frontend:sandbox"
  "upo-gateway:sandbox"
  "upo-user-service:sandbox"
  "upo-product-service:sandbox"
  "upo-order-service:sandbox"
  "upo-notification-service:sandbox"
)

REQUIRED_DEPLOYMENTS=(
  "mysql"
  "redis"
  "rabbitmq"
  "mongodb"
  "user-service"
  "product-service"
  "order-service"
  "notification-service"
  "gateway"
  "frontend"
)

require_cmd() {
  local cmd="$1"
  if ! command -v "$cmd" >/dev/null 2>&1; then
    echo "Missing required command: $cmd" >&2
    exit 1
  fi
}

cleanup() {
  if [[ -n "${PORT_FORWARD_PID:-}" ]]; then
    kill "$PORT_FORWARD_PID" >/dev/null 2>&1 || true
  fi
}

trap cleanup EXIT

require_cmd docker
require_cmd kubectl
require_cmd kind

if ! kind get clusters | grep -qx "$CLUSTER_NAME"; then
  if [[ "$CREATE_CLUSTER_IF_MISSING" != "true" ]]; then
    echo "kind cluster '$CLUSTER_NAME' does not exist and CREATE_KIND_CLUSTER_IF_MISSING=false" >&2
    exit 1
  fi
  echo "Creating kind cluster '$CLUSTER_NAME'..."
  kind create cluster --name "$CLUSTER_NAME"
fi

echo "Using kubectl context '$KUBECTL_CONTEXT'..."
kubectl config use-context "$KUBECTL_CONTEXT" >/dev/null

if [[ "$LOAD_SANDBOX_IMAGES" == "true" ]]; then
  echo "Loading sandbox images into kind..."
  for image in "${REQUIRED_IMAGES[@]}"; do
    docker image inspect "$image" >/dev/null 2>&1 || {
      echo "Required local image not found: $image" >&2
      exit 1
    }
    kind load docker-image --name "$CLUSTER_NAME" "$image" >/dev/null
  done
fi

echo "Applying sandbox manifests..."
kubectl apply -k "$ROOT_DIR/infra/k8s/sandbox" >/dev/null

echo "Waiting for sandbox deployments..."
for deployment in "${REQUIRED_DEPLOYMENTS[@]}"; do
  kubectl rollout status "deployment/${deployment}" -n "$NAMESPACE" --timeout="$ROLLOUT_TIMEOUT" >/dev/null
done

echo "Sandbox deployments are ready."
kubectl get pods,svc,ingress -n "$NAMESPACE"

if [[ "$PORT_FORWARD_GATEWAY" == "true" ]]; then
  echo "Port-forwarding gateway to localhost:${PORT_FORWARD_PORT} for a health check..."
  kubectl port-forward -n "$NAMESPACE" svc/gateway "${PORT_FORWARD_PORT}:8000" >/tmp/modern-upo-port-forward.log 2>&1 &
  PORT_FORWARD_PID=$!
  sleep 3
  curl -fsS "http://127.0.0.1:${PORT_FORWARD_PORT}/health"
  echo
fi

echo "kind sandbox validation completed successfully."
