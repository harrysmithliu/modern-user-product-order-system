#!/usr/bin/env bash

set -euo pipefail

if ! command -v aws >/dev/null 2>&1; then
  echo "aws CLI is required."
  exit 1
fi

INSTANCE_ID="${1:-}"
REGION="${2:-}"

if [[ -z "${INSTANCE_ID}" || -z "${REGION}" ]]; then
  echo "Usage: $0 <instance-id> <region> [timezone]"
  echo "Example: $0 i-0123456789abcdef0 us-east-1 America/Toronto"
  exit 1
fi

TIMEZONE="${3:-America/Toronto}"
START_CRON="${START_CRON:-cron(0 9 ? * MON-FRI *)}"
STOP_CRON="${STOP_CRON:-cron(35 17 ? * MON-FRI *)}"

ACCOUNT_ID="$(aws sts get-caller-identity --query Account --output text)"
ROLE_NAME="${SCHEDULER_ROLE_NAME:-modern-upo-ec2-scheduler-role}"
ROLE_ARN="arn:aws:iam::${ACCOUNT_ID}:role/${ROLE_NAME}"
START_SCHEDULE_NAME="${START_SCHEDULE_NAME:-modern-upo-start-weekday-0900}"
STOP_SCHEDULE_NAME="${STOP_SCHEDULE_NAME:-modern-upo-stop-weekday-1735}"

INSTANCE_ARN="arn:aws:ec2:${REGION}:${ACCOUNT_ID}:instance/${INSTANCE_ID}"

echo "Ensuring scheduler IAM role exists..."
if ! aws iam get-role --role-name "${ROLE_NAME}" >/dev/null 2>&1; then
  aws iam create-role \
    --role-name "${ROLE_NAME}" \
    --assume-role-policy-document '{
      "Version": "2012-10-17",
      "Statement": [
        {
          "Effect": "Allow",
          "Principal": {
            "Service": "scheduler.amazonaws.com"
          },
          "Action": "sts:AssumeRole"
        }
      ]
    }' >/dev/null
fi

echo "Updating scheduler IAM policy..."
aws iam put-role-policy \
  --role-name "${ROLE_NAME}" \
  --policy-name modern-upo-ec2-scheduler-inline \
  --policy-document "{
    \"Version\": \"2012-10-17\",
    \"Statement\": [
      {
        \"Effect\": \"Allow\",
        \"Action\": [
          \"ec2:StartInstances\",
          \"ec2:StopInstances\"
        ],
        \"Resource\": \"${INSTANCE_ARN}\"
      }
    ]
  }" >/dev/null

upsert_schedule() {
  local name="$1"
  local expression="$2"
  local target_arn="$3"
  local input_json="$4"

  if aws scheduler get-schedule --name "${name}" >/dev/null 2>&1; then
    aws scheduler update-schedule \
      --name "${name}" \
      --description "Managed by setup-ec2-workday-scheduler.sh" \
      --schedule-expression "${expression}" \
      --schedule-expression-timezone "${TIMEZONE}" \
      --flexible-time-window '{"Mode":"OFF"}' \
      --target "{\"RoleArn\":\"${ROLE_ARN}\",\"Arn\":\"${target_arn}\",\"Input\":\"${input_json}\"}" >/dev/null
  else
    aws scheduler create-schedule \
      --name "${name}" \
      --description "Managed by setup-ec2-workday-scheduler.sh" \
      --schedule-expression "${expression}" \
      --schedule-expression-timezone "${TIMEZONE}" \
      --flexible-time-window '{"Mode":"OFF"}' \
      --target "{\"RoleArn\":\"${ROLE_ARN}\",\"Arn\":\"${target_arn}\",\"Input\":\"${input_json}\"}" >/dev/null
  fi
}

echo "Upserting start schedule..."
upsert_schedule \
  "${START_SCHEDULE_NAME}" \
  "${START_CRON}" \
  "arn:aws:scheduler:::aws-sdk:ec2:startInstances" \
  "{\"InstanceIds\":[\"${INSTANCE_ID}\"]}"

echo "Upserting stop schedule..."
upsert_schedule \
  "${STOP_SCHEDULE_NAME}" \
  "${STOP_CRON}" \
  "arn:aws:scheduler:::aws-sdk:ec2:stopInstances" \
  "{\"InstanceIds\":[\"${INSTANCE_ID}\"]}"

echo "Done."
echo "- Start schedule: ${START_SCHEDULE_NAME} (${START_CRON}, ${TIMEZONE})"
echo "- Stop schedule: ${STOP_SCHEDULE_NAME} (${STOP_CRON}, ${TIMEZONE})"
echo "- Scheduler role: ${ROLE_ARN}"
