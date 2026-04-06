# Sandbox EC2 Online Deployment

This directory contains the low-cost, long-running deployment baseline for the
`sandbox-ec2-online` branch.

It intentionally differs from the cloud-native production material in
`infra/aws/prod` and `infra/k8s/prod`:

- `main`
  - keeps the EKS / Kubernetes / AWS production architecture baseline
- `sandbox-ec2-online`
  - targets a single EC2 host
  - runs the full app stack with Docker Compose
  - exposes the app through Nginx
  - uses Let's Encrypt for HTTPS
  - supports GitHub Actions deployment over SSH

## What This Stack Includes

- reverse proxy
  - Nginx serves the frontend and proxies `/api` to the gateway
- app services
  - frontend
  - gateway
  - user-service
  - product-service
  - order-service
  - notification-service
- stateful dependencies on the same EC2 host
  - MySQL
  - Redis
  - RabbitMQ
  - MongoDB
- certificate flow
  - HTTP bootstrap config
  - Certbot webroot challenge
  - HTTPS config switch after certificate issuance
- GitHub Actions deployment baseline
  - remote deploy to EC2 over SSH

## Included Files

- `env.example`
  - example environment values for the EC2 host
- `docker-compose.yml`
  - single-host long-running stack
- `nginx/default.http.conf`
  - initial HTTP-only reverse proxy for first boot and ACME challenge
- `nginx/default.https.conf`
  - HTTPS reverse proxy config after certificates exist
- `bootstrap-ec2.sh`
  - installs Docker, Compose plugin, Git, and prepares host directories
- `deploy.sh`
  - renders the active Nginx config and runs `docker compose up -d --build`
- `issue-certificate.sh`
  - requests or renews Let's Encrypt certificates using a Certbot container

## Recommended AWS Shape

For a low-cost always-on demo baseline:

- one Ubuntu EC2 instance
- one public IPv4 address
- one gp3 EBS volume
- one domain or subdomain
- Docker Compose for all workloads

This is intentionally cheaper than keeping a full EKS environment online.

## Suggested Instance Baseline

- instance family
  - `t3.small` or `t4g.small`
- disk
  - 30 to 40 GB gp3
- OS
  - Ubuntu 24.04 LTS

If you choose Graviton (`t4g.*`), make sure every image you build supports
`linux/arm64`.

## First-Time EC2 Setup

SSH into the host and run:

```bash
sudo bash infra/aws/sandbox-ec2/bootstrap-ec2.sh
```

This script:

- installs Docker Engine
- installs the Docker Compose plugin
- enables Docker on boot
- installs Git and curl
- creates `/opt/modern-upo`
- prepares certificate and webroot directories

After that, clone the repository to `/opt/modern-upo/repo`.

## Environment File

Copy the template and fill real values:

```bash
cp infra/aws/sandbox-ec2/env.example infra/aws/sandbox-ec2/.env.ec2.local
```

The important values are:

- `APP_DOMAIN`
- `LETSENCRYPT_EMAIL`
- `SANDBOX_JWT_SECRET`
- `MYSQL_ROOT_PASSWORD`
- `MYSQL_APP_PASSWORD`
- `RABBITMQ_DEFAULT_PASS`
- `MONGO_INITDB_ROOT_PASSWORD`

This env file must stay local to the EC2 host and must not be committed.

## Deploy the Stack

From the repository root on the EC2 host:

```bash
source infra/aws/sandbox-ec2/.env.ec2.local
bash infra/aws/sandbox-ec2/deploy.sh
```

That first deployment uses the HTTP Nginx config so the ACME challenge can work.

## Issue the TLS Certificate

After DNS points to the EC2 public IP:

```bash
source infra/aws/sandbox-ec2/.env.ec2.local
bash infra/aws/sandbox-ec2/issue-certificate.sh
```

This script:

- runs Certbot using the webroot challenge
- writes certs to the shared LetsEncrypt volume
- switches Nginx from HTTP to HTTPS config
- reloads the reverse proxy

## URLs After HTTPS

- app
  - `https://${APP_DOMAIN}`
- frontend
  - served on `/`
- API
  - served on `/api`

## GitHub Actions Deployment

The repository includes a workflow that can deploy this branch to EC2.

Required GitHub secrets:

- `SANDBOX_EC2_HOST`
- `SANDBOX_EC2_USER`
- `SANDBOX_EC2_SSH_KEY`
- `SANDBOX_EC2_APP_DIR`
- `SANDBOX_EC2_ENV_FILE`

Required GitHub variables:

- `SANDBOX_EC2_BRANCH`
  - usually `sandbox-ec2-online`

The workflow assumes:

- the EC2 host already has Docker installed
- the repository is already cloned
- the env file is provided by GitHub Actions as a secret payload

## Runtime Ports

Public:

- `80`
- `443`

Internal only:

- MySQL `3306`
- Redis `6379`
- RabbitMQ `5672`
- RabbitMQ management `15672`
- MongoDB `27017`
- gateway `8000`
- user-service `8001`
- product-service `8002`
- order-service `8080`

## Long-Running Ops Checklist

At minimum, after each deploy verify:

```bash
docker compose -f infra/aws/sandbox-ec2/docker-compose.yml ps
docker compose -f infra/aws/sandbox-ec2/docker-compose.yml logs --tail=100 gateway
docker compose -f infra/aws/sandbox-ec2/docker-compose.yml logs --tail=100 order-service
curl -I http://127.0.0.1
curl -I https://${APP_DOMAIN}
```

Recommended recurring checks:

- certificate renewal still succeeds
- database volume usage stays healthy
- `docker compose ps` shows all services healthy or running
- Prometheus / Grafana remain reachable if you choose to expose them privately

## Relationship to Production Architecture

This directory is intentionally the lower-cost deployment track for the online
demo environment.

For the full cloud-native production track, keep using:

- [infra/aws/prod/README.md](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/infra/aws/prod/README.md)
- [infra/k8s/prod/README.md](/Users/harryliu/Documents/workspace/portfolio/pj-modern-user-product-order-system/modern-user-product-order-system/infra/k8s/prod/README.md)
