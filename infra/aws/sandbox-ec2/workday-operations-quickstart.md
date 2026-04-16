# Workday Operations Quickstart

This is the shortest possible operating guide for the cost-optimized
`sandbox-ec2-online` flow.

Goal:

- keep the EC2 demo online only during weekday business hours
- make `startup` the only manual action when you want to bring it back
- let deployment, DNS sync, and certificate checks happen automatically on boot

## What Happens Automatically

When the EC2 instance boots:

- `deploy.sh` starts the Docker stack
- `sync-route53-record.sh` updates the `A` record to the current EC2 public IPv4
- `issue-certificate.sh` can run if `AUTO_CERTBOT_ON_STARTUP=true`

When the workday ends:

- the instance stops
- SSH disconnects as expected

## Manual Actions You Need

Only two actions:

- start the instance in the morning or when you want a temporary night/weekend demo
- stop the instance when you are done, if it is not already scheduled to stop

## AWS Console Version

### Start

1. Open the AWS EC2 console.
2. Select the demo instance.
3. Click `Start instance`.
4. Wait until the instance status becomes `running`.
5. Open the site through `https://<your-domain>`.

### Stop

1. Open the AWS EC2 console.
2. Select the demo instance.
3. Click `Stop instance`.
4. Wait until the instance status becomes `stopped`.

## AWS CLI Version

### Start

```bash
aws ec2 start-instances --instance-ids <instance-id> --region <region>
```

### Stop

```bash
aws ec2 stop-instances --instance-ids <instance-id> --region <region>
```

### Check State

```bash
aws ec2 describe-instances \
  --instance-ids <instance-id> \
  --region <region> \
  --query 'Reservations[0].Instances[0].State.Name' \
  --output text
```

## After Startup

If startup automation is installed correctly, you do not need to SSH in for the
common path. The instance should boot, deploy, and update DNS on its own.

If you want to verify manually:

```bash
ssh ubuntu@<your-domain-or-public-ip>
systemctl status modern-upo-online-startup.service --no-pager
```

## DNS And Registrar

If your domain is already delegated to Route53:

- you do **not** need to go to the registrar website every time the EC2 IP changes
- the boot-time DNS sync will update the Route53 `A` record for you

You only need the registrar website for the one-time setup where you point the
domain's nameservers to Route53.

If your domain is still managed by the registrar's own DNS:

- then yes, you would need to update the A record there unless you move DNS to Route53

## Night Or Weekend Temporary Start

Use the same start action as the morning flow:

- AWS Console: `Start instance`
- AWS CLI: `aws ec2 start-instances ...`

Once the machine boots, the startup automation will deploy the stack and sync DNS
without further manual steps.

## Minimal Checklist

Before leaving the instance to automation:

- `APP_DOMAIN` is set
- `AWS_REGION` is set
- `ROUTE53_HOSTED_ZONE_ID` is set
- the EC2 instance role can change Route53 records
- the AWS user/role you use to create the scheduler can manage EC2 and Scheduler

