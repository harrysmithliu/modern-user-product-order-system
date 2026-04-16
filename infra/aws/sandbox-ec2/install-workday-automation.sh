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

WORKDAY_TIMEZONE="${WORKDAY_TIMEZONE:-America/Toronto}"
WORKDAY_STOP_HOUR="${WORKDAY_STOP_HOUR:-17}"
WORKDAY_STOP_MINUTE="${WORKDAY_STOP_MINUTE:-30}"
EC2_APP_USER="${EC2_APP_USER:-ubuntu}"

SYSTEMD_UNIT="/etc/systemd/system/modern-upo-online-startup.service"
CRON_FILE="/etc/cron.d/modern-upo-online-cost"
LOG_FILE="/var/log/modern-upo-online-ops.log"

echo "Installing systemd startup unit..."
sudo tee "${SYSTEMD_UNIT}" >/dev/null <<UNIT
[Unit]
Description=Modern UPO startup workflow for workday mode
After=network-online.target docker.service
Wants=network-online.target docker.service

[Service]
Type=oneshot
User=${EC2_APP_USER}
WorkingDirectory=${ROOT_DIR}
ExecStart=${AWS_DIR}/workday-startup.sh ${ENV_FILE}
TimeoutStartSec=1800

[Install]
WantedBy=multi-user.target
UNIT

echo "Installing cost-aware cron schedule..."
sudo tee "${CRON_FILE}" >/dev/null <<CRON
SHELL=/bin/bash
PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin
CRON_TZ=${WORKDAY_TIMEZONE}

# Keep DNS aligned with current instance public IP during work hours.
*/15 9-17 * * 1-5 ${EC2_APP_USER} cd ${ROOT_DIR} && ${AWS_DIR}/sync-route53-record.sh ${ENV_FILE} >> ${LOG_FILE} 2>&1

# Check certificate weekly in work window (keep-until-expiring).
15 11 * * 1 ${EC2_APP_USER} cd ${ROOT_DIR} && ${AWS_DIR}/issue-certificate.sh ${ENV_FILE} >> ${LOG_FILE} 2>&1

# Stop instance after demo hours to reduce cost.
${WORKDAY_STOP_MINUTE} ${WORKDAY_STOP_HOUR} * * 1-5 root /sbin/shutdown -h now "off-hours cost optimization"
CRON

sudo touch "${LOG_FILE}"
sudo chown "${EC2_APP_USER}:${EC2_APP_USER}" "${LOG_FILE}"

sudo systemctl daemon-reload
sudo systemctl enable modern-upo-online-startup.service

echo "Installed."
echo "- systemd unit: ${SYSTEMD_UNIT}"
echo "- cron file: ${CRON_FILE}"
echo "- log file: ${LOG_FILE}"
echo
echo "Next:"
echo "1) Reboot once to verify startup automation:"
echo "   sudo reboot"
echo "2) Confirm service status after reboot:"
echo "   systemctl status modern-upo-online-startup.service --no-pager"
