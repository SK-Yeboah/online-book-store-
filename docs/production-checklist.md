# Production readiness checklist

Use this before enabling public traffic or flipping `DEPLOY_TO_AWS=true`.

## Code / config (done in-repo)

- [x] Flyway owns schema; prod `ddl-auto=validate`
- [x] Prod secrets via env (no hardcoded keys)
- [x] Actuator limited to `health` + `prometheus`
- [x] `/actuator/prometheus` requires **ADMIN** JWT in prod (`security.actuator.prometheus-public=false`)
- [x] Swagger/OpenAPI disabled in prod
- [x] Rate-limit defaults **60/min** in prod (override only with intent)
- [x] `payment.webhook-secret` required for mock; Paystack uses signature verification
- [x] Local monitoring + k6 scripts documented (`docs/monitoring.md`, `docs/load-testing.md`)

## Staging gate (required before prod)

- [ ] Commit and push latest `develop` / release branch
- [ ] Deploy to **staging** with Paystack **test** keys
- [ ] Set HTTPS public URL for webhooks: `https://<api>/api/payments/webhook/paystack`
- [ ] Register webhook in Paystack dashboard; confirm signature verification
- [ ] Set `PAYMENT_CALLBACK_URL` to real frontend HTTPS callback
- [ ] Set `CORS_ALLOWED_ORIGINS` to real frontend origin(s) only
- [ ] ALB/target health check → `GET /actuator/health` (expect `UP`)
- [ ] Smoke: register → login → cart → checkout → Paystack test pay → webhook → `CONFIRMED`
- [ ] Short soak on staging (e.g. k6 10 VUs × 10m with reused auth) — error rate &lt; 1%
- [ ] Confirm rate limits are **not** left at load-test values (10000+)

## Secrets (Secrets Manager / ECS task)

| Variable | Notes |
|----------|--------|
| `JWT_SECRET` | `openssl rand -hex 32` |
| `DB_HOST` `DB_NAME` `DB_USERNAME` `DB_PASSWORD` | RDS; SSL URL already in prod props |
| `PAYSTACK_SECRET_KEY` `PAYSTACK_PUBLIC_KEY` | **live** keys only on prod |
| `PAYMENT_CALLBACK_URL` | HTTPS frontend |
| `PAYMENT_WEBHOOK_SECRET` | Required if `PAYMENT_PROVIDER=mock`; optional for paystack |
| `CORS_ALLOWED_ORIGINS` | Comma-separated HTTPS origins |
| `REDIS_HOST` / `REDIS_PORT` | ElastiCache or equivalent |

## AWS infra (IaC)

Create staging with Terraform (not the Console runbook):

```bash
cd infra/envs/staging
cp terraform.tfvars.example terraform.tfvars   # edit secrets / image
terraform init && terraform apply
```

Full steps: [`infra/README.md`](../infra/README.md). Manual fallback: [`aws-restore-runbook.md`](./aws-restore-runbook.md).

## AWS deploy enablement

CI keeps deploy jobs `if: false` until the above staging gate passes and these GitHub secrets/vars exist:

- AWS credentials / OIDC role
- ECS cluster, service, task definition names
- Image pull permissions for GHCR

Then set `DEPLOY_TO_AWS: true` in `.github/workflows/ci-cd.yml` (or a repo variable) and deploy **staging first**, then `main`.

## Ops (ongoing)

- [ ] Automated RDS backups + retention
- [ ] Log aggregation (CloudWatch / OpenSearch) for JSON prod logs
- [ ] Alerts on 5xx rate, health DOWN, payment webhook failures
- [ ] Prometheus/AMP scrapes metrics **privately** (ADMIN token or internal network only)
- [ ] Incident runbook: failed payments, Flyway migrate, rollback image

## Explicitly out of scope for “API prod ready”

Email notifications, GDPR export/delete, and rich admin UI are product follow-ups — not required to serve paid checkout safely, but plan them for a full retail launch.
