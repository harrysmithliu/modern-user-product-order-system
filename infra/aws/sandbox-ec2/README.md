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
  - installs Docker, Compose plugin, Git, AWS CLI v2, and prepares host directories
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

Additional optional values for weekday cost optimization:

- `AWS_REGION`
- `ROUTE53_HOSTED_ZONE_ID`
- `ROUTE53_RECORD_TTL`
- `AUTO_CERTBOT_ON_STARTUP`
- `EC2_APP_USER`
- `WORKDAY_TIMEZONE`

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

## Workday Cost-Optimized Mode (No Elastic IP)

This mode is for a lower monthly bill:

- keep demo online only on weekdays during business hours
- avoid Elastic IP recurring IPv4 cost
- auto-update Route53 `A` record to the current EC2 public IPv4 on startup
- auto-stop the instance after work hours

### Added Scripts

- `sync-route53-record.sh`
  - UPSERT Route53 `A` record using current EC2 public IPv4
- `workday-startup.sh`
  - deploy stack, sync DNS, and optionally run cert check
- `install-workday-automation.sh`
  - installs systemd startup unit and on-instance cron automation (DNS + cert checks only)
- `setup-ec2-workday-scheduler.sh`
  - creates AWS EventBridge Scheduler start/stop schedules (single stop/start source)

### IAM Requirements

EC2 instance profile should allow:

- `route53:ChangeResourceRecordSets` on your hosted zone

The IAM identity running `setup-ec2-workday-scheduler.sh` should allow:

- `scheduler:CreateSchedule`
- `scheduler:UpdateSchedule`
- `scheduler:GetSchedule`
- `iam:CreateRole`
- `iam:PutRolePolicy`
- `iam:GetRole`
- `ec2:StartInstances`
- `ec2:StopInstances`

### Deployment Steps

1. Prepare environment values on EC2:

```bash
cp infra/aws/sandbox-ec2/env.example infra/aws/sandbox-ec2/.env.ec2.local
```

At minimum set:

- `APP_DOMAIN`
- `AWS_REGION`
- `ROUTE53_HOSTED_ZONE_ID`

2. Run first deployment:

```bash
source infra/aws/sandbox-ec2/.env.ec2.local
bash infra/aws/sandbox-ec2/deploy.sh infra/aws/sandbox-ec2/.env.ec2.local
```

3. Install startup + weekday automation on EC2:

```bash
bash infra/aws/sandbox-ec2/install-workday-automation.sh infra/aws/sandbox-ec2/.env.ec2.local
```

4. Create AWS weekday schedules (from a machine with AWS CLI credentials):

```bash
bash infra/aws/sandbox-ec2/setup-ec2-workday-scheduler.sh i-0123456789abcdef0 us-east-1 America/Toronto
```

Default schedule produced by this script:

- start: weekdays `09:00` (America/Toronto)
- stop: weekdays `17:35` (America/Toronto)

Important:

- keep **only one** stop/start control plane to avoid unexpected shutdown timing
- recommended: EventBridge Scheduler only
- `install-workday-automation.sh` does not install local shutdown cron anymore

5. Verify:

```bash
systemctl status modern-upo-online-startup.service --no-pager
sudo cat /etc/cron.d/modern-upo-online-cost
curl -I https://${APP_DOMAIN}
curl -fsS https://${APP_DOMAIN}/api/health
```

### Cost Notes

- This mode can significantly reduce `EC2 - Compute` cost by turning off
  instance hours outside demo time.
- It avoids permanent Elastic IP charges, but requires Route53 dynamic record
  updates.
- If you need the simplest networking behavior, use Elastic IP instead.

## Quickstart

For the shortest operator checklist, see:

- [workday-operations-quickstart.md](workday-operations-quickstart.md)

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
- the GitHub repository is public, or the EC2 host already has permission to `git clone` and `git pull` this repository

### Recommended GitHub Secret Values

For the current deployment baseline, use:

- `SANDBOX_EC2_HOST`
  - the SSH host for the EC2 instance
  - recommended value in no-EIP mode: your domain, for example `demo.example.com`
  - recommended value in EIP mode: the fixed EIP IPv4
- `SANDBOX_EC2_USER`
  - `ubuntu`
- `SANDBOX_EC2_SSH_KEY`
  - the full contents of the EC2 private key file
  - for example, the contents of `~/.ssh/modern-upo-online-key.pem`
- `SANDBOX_EC2_APP_DIR`
  - `/opt/modern-upo/repo`
- `SANDBOX_EC2_ENV_FILE`
  - the full contents of `infra/aws/sandbox-ec2/.env.ec2.local`
  - this should stay a GitHub secret and must not be committed

### Recommended GitHub Variable Values

- `SANDBOX_EC2_BRANCH`
  - `sandbox-ec2-online`

After the secrets are configured, every push to `sandbox-ec2-online` will:

- connect to the EC2 host over SSH
- update the checked-out branch
- rewrite `infra/aws/sandbox-ec2/.env.ec2.local`
- run the deployment script
- wait for the remote homepage and gateway health endpoint to become healthy

### GitHub Actions Setup Checklist

1. Open GitHub:
   `Settings` -> `Secrets and variables` -> `Actions`
2. Under `Secrets`, create:
   - `SANDBOX_EC2_HOST`
   - `SANDBOX_EC2_USER`
   - `SANDBOX_EC2_SSH_KEY`
   - `SANDBOX_EC2_APP_DIR`
   - `SANDBOX_EC2_ENV_FILE`
3. Under `Variables`, create:
   - `SANDBOX_EC2_BRANCH`
4. Confirm the branch already exists in GitHub:
   - `sandbox-ec2-online`
5. Confirm the EC2 host is already prepared:
   - Docker and Compose are installed
   - the repository can be cloned or pulled from that host
   - port `22` is reachable from GitHub Actions runners
6. Push a small commit to `sandbox-ec2-online`.
7. Open the `Actions` tab and confirm:
   - the CI workflow passes
   - `Deploy Sandbox EC2 Online` completes
   - the site responds at `https://${APP_DOMAIN}`
   - `https://${APP_DOMAIN}/api/health` returns success

### Recommended First Verification Push

After you finish the GitHub configuration, the fastest smoke test is:

```bash
git checkout sandbox-ec2-online
git commit --allow-empty -m "chore: verify sandbox ec2 deploy"
git push origin sandbox-ec2-online
```

That gives you a clean Actions run without mixing the first deployment check with unrelated code changes.

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

- [infra/aws/prod/README.md](../prod/README.md)
- [infra/k8s/prod/README.md](../../k8s/prod/README.md)
