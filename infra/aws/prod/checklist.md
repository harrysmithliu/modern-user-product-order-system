# AWS Deployment Checklist

## Before Push

- confirm the target branch has passed local validation
- confirm the latest `sandbox` state has been smoke tested
- confirm local Docker images are up to date
- confirm the intended `IMAGE_TAG` is set
- confirm AWS CLI login works for the selected profile

## Before ECR Push

- verify `env.example` has been copied to a local env file
- verify `AWS_ACCOUNT_ID`, `AWS_REGION`, and `AWS_PROFILE`
- verify all `ECR_*_REPO` values match the intended naming scheme
- verify the following local images exist:
  - `upo-frontend:sandbox`
  - `upo-gateway:sandbox`
  - `upo-user-service:sandbox`
  - `upo-product-service:sandbox`
  - `upo-order-service:sandbox`
  - `upo-notification-service:sandbox`

## Before EKS Apply

- verify the EKS cluster already exists
- verify `kubectl` can reach the cluster
- verify the target namespace
- verify secrets are ready for:
  - JWT
  - MySQL
  - RabbitMQ
  - MongoDB
- verify the production manifest set is present under `infra/k8s/prod`

## After EKS Apply

- verify pods are `Running`
- verify services and ingress objects exist
- verify health endpoints respond through the gateway
- verify Prometheus or CloudWatch visibility is in place
- verify a smoke-test order can be created and observed

## Optional Next Enhancements

- ALB ingress annotations
- TLS certificates
- RDS and ElastiCache integration
- External Secrets integration
- GitHub Actions deployment workflow
