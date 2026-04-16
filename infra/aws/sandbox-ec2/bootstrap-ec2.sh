#!/usr/bin/env bash

set -euo pipefail

if [[ "$(id -u)" -ne 0 ]]; then
  echo "Please run as root or with sudo."
  exit 1
fi

export DEBIAN_FRONTEND=noninteractive

apt-get update
apt-get install -y ca-certificates curl gnupg git unzip gettext-base awscli

install -m 0755 -d /etc/apt/keyrings
if [[ ! -f /etc/apt/keyrings/docker.asc ]]; then
  curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
  chmod a+r /etc/apt/keyrings/docker.asc
fi

if [[ ! -f /etc/apt/sources.list.d/docker.list ]]; then
  ARCH="$(dpkg --print-architecture)"
  UBUNTU_CODENAME="$(
    . /etc/os-release
    echo "${VERSION_CODENAME}"
  )"
  echo \
    "deb [arch=${ARCH} signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu ${UBUNTU_CODENAME} stable" \
    >/etc/apt/sources.list.d/docker.list
fi

apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin

systemctl enable docker
systemctl start docker

install -d -m 0755 /opt/modern-upo/repo
install -d -m 0755 /opt/modern-upo/repo/infra/aws/sandbox-ec2/runtime/certbot/www
install -d -m 0755 /opt/modern-upo/repo/infra/aws/sandbox-ec2/runtime/letsencrypt

echo "Bootstrap complete."
echo "Next steps:"
echo "1. clone the repo into /opt/modern-upo/repo"
echo "2. copy env.example to .env.ec2.local"
echo "3. run bash infra/aws/sandbox-ec2/deploy.sh"
