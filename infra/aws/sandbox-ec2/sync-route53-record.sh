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

if [[ -z "${APP_DOMAIN:-}" ]]; then
  echo "APP_DOMAIN is required in ${ENV_FILE}."
  exit 1
fi

if [[ -z "${ROUTE53_HOSTED_ZONE_ID:-}" ]]; then
  echo "ROUTE53_HOSTED_ZONE_ID is required in ${ENV_FILE}."
  exit 1
fi

AWS_BIN="$(command -v aws || true)"
if [[ -z "${AWS_BIN}" && -x /usr/local/bin/aws ]]; then
  AWS_BIN="/usr/local/bin/aws"
fi
if [[ -z "${AWS_BIN}" && -x /usr/bin/aws ]]; then
  AWS_BIN="/usr/bin/aws"
fi
if [[ -z "${AWS_BIN}" && -x /snap/bin/aws ]]; then
  AWS_BIN="/snap/bin/aws"
fi
if [[ -z "${AWS_BIN}" ]]; then
  echo "aws CLI is required."
  exit 1
fi

TOKEN="$(curl -fsS -X PUT "http://169.254.169.254/latest/api/token" -H "X-aws-ec2-metadata-token-ttl-seconds: 300")"
PUBLIC_IP="$(curl -fsS -H "X-aws-ec2-metadata-token: ${TOKEN}" "http://169.254.169.254/latest/meta-data/public-ipv4")"

if [[ -z "${PUBLIC_IP}" ]]; then
  echo "Unable to read public IPv4 from instance metadata."
  exit 1
fi

TTL="${ROUTE53_RECORD_TTL:-60}"
RECORD_NAME="${APP_DOMAIN}"
if [[ "${RECORD_NAME}" != *"." ]]; then
  RECORD_NAME="${RECORD_NAME}."
fi

if [[ -n "${AWS_REGION:-}" ]]; then
  AWS_REGION_ARG=(--region "${AWS_REGION}")
else
  AWS_REGION_ARG=()
fi

CHANGE_BATCH="$(cat <<JSON
{
  "Comment": "Auto-updated by sandbox-ec2 sync-route53-record.sh",
  "Changes": [
    {
      "Action": "UPSERT",
      "ResourceRecordSet": {
        "Name": "${RECORD_NAME}",
        "Type": "A",
        "TTL": ${TTL},
        "ResourceRecords": [
          {
            "Value": "${PUBLIC_IP}"
          }
        ]
      }
    }
  ]
}
JSON
)"

"${AWS_BIN}" "${AWS_REGION_ARG[@]}" route53 change-resource-record-sets \
  --hosted-zone-id "${ROUTE53_HOSTED_ZONE_ID}" \
  --change-batch "${CHANGE_BATCH}" >/dev/null

echo "Route53 record updated: ${APP_DOMAIN} -> ${PUBLIC_IP}"
