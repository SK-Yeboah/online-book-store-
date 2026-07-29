# Terraform — Online Book Store (AWS staging)

Creates the lab environment from `docs/aws-restore-runbook.md` with one command:

**VPC → ALB → ECS Fargate → RDS MySQL → ElastiCache Redis (serverless) → Secrets**

## Prerequisites

1. [Terraform](https://developer.hashicorp.com/terraform/install) ≥ 1.5  
2. AWS CLI credentials for account/region `us-east-1` (`aws sts get-caller-identity`)  
3. A container image in GHCR that ECS can pull (CI builds on push to `develop`/`main`)  
4. If the package is **private**: a GitHub PAT with `read:packages`

## Cost warning

NAT Gateway alone is roughly **$0.045/hour**. Destroy when idle:

```bash
cd infra/envs/staging
terraform destroy
```

## Apply (create the house)

```bash
cd infra/envs/staging
cp terraform.tfvars.example terraform.tfvars
# edit terraform.tfvars — set ghcr_* if image is private

terraform init
terraform plan
terraform apply
```

When finished, Terraform prints:

| Output | Use |
|--------|-----|
| `health_url` | `curl` this until `{"status":"UP"}` |
| `github_actions_vars` | Paste into GitHub Actions **variables** |

Example health check:

```bash
curl -s "$(terraform output -raw health_url)"
```

If the service is unhealthy, check logs:

```bash
aws logs tail "$(terraform output -raw log_group_name)" --follow --region us-east-1
```

## Wire CI (move furniture on every push)

1. GitHub → Settings → Secrets: `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`  
2. GitHub → Settings → Variables: values from `terraform output github_actions_vars`  
   - `STAGING_ECS_CLUSTER`  
   - `STAGING_ECS_SERVICE`  
   - `STAGING_ECS_TASK_DEFINITION`  
3. In `.github/workflows/ci-cd.yml`, change `deploy-staging` from `if: false` to run on `develop` pushes.  
4. Leave `deploy-production` disabled.  
5. `git push origin develop` → CI builds image → ECS rolls out.

The ECS service uses `lifecycle { ignore_changes = [task_definition] }` so Terraform will **not** overwrite the image tag that CI deploys.

## Payments on staging

Default `payment_provider = "mock"` (good for ALB/ECS smoke).

For Paystack test mode, set keys in `terraform.tfvars` and re-apply (updates the `bookstore/app-config` secret), then force a new ECS deployment:

```bash
aws ecs update-service \
  --cluster "$(terraform output -raw ecs_cluster_name)" \
  --service "$(terraform output -raw ecs_service_name)" \
  --force-new-deployment \
  --region us-east-1
```

### HTTPS (required for Paystack webhooks)

Paystack only accepts `https://` webhook URLs. Enable ACM + HTTPS listener:

1. Own a domain whose **public hosted zone is in Route53** in this account.
2. In `terraform.tfvars`:

```hcl
enable_https    = true
domain_name     = "api.staging.yourdomain.com"   # must be under that zone
route53_zone_id = "Zxxxxxxxxxxxxx"               # Route53 → Hosted zones → Zone ID
```

3. Apply:

```bash
terraform apply
terraform output paystack_webhook_url
# → https://api.staging.yourdomain.com/api/payments/webhook/paystack
```

Terraform will:

- Request an ACM certificate (DNS validation)
- Create validation + alias records in Route53
- Add HTTPS:443 on the ALB
- Redirect HTTP:80 → HTTPS

4. Paste `paystack_webhook_url` into Paystack → Settings → API Keys & Webhooks.

Without a domain, keep using the app **verify** endpoint after checkout, or a temporary tunnel — Paystack will reject plain `http://…elb.amazonaws.com` URLs.

## Layout

```text
infra/
  modules/
    network/   # VPC, subnets, NAT, security groups
    data/      # RDS + Redis serverless
    alb/       # ALB + TG + optional ACM/HTTPS
    ecs/       # cluster, task, service, IAM, secrets
  envs/
    staging/   # what you apply
```

## State

Default is **local** `terraform.tfstate` (gitignored). For a shared team backend, uncomment the S3 backend block in `main.tf`.

## Teardown

```bash
cd infra/envs/staging
terraform destroy
```

Wait until NAT/EIP are gone so they stop billing. Manual runbook teardown is in `docs/aws-restore-runbook.md` §11 if anything is left outside Terraform.
