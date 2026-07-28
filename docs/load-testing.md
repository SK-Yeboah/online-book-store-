# End-to-end load testing

Measure the full buyer path (not just `GET /api/books`) while watching Grafana.

## Flow under test

**Default (auth reused per VU):** each virtual user registers/logs in once, then repeats:

```text
add to cart → checkout → mock payment intent → mock webhook → order CONFIRMED
```

**Auth stress:** set `AUTH_EVERY_ITER=true` to register+login on every iteration (higher E2E latency; measures BCrypt under load).

## Prerequisites

1. Stack up: `docker compose up -d`
2. App uses **mock** payments (Compose default `PAYMENT_PROVIDER=mock`)
3. High rate limits **only for the load-test window** (prod default is 60/min). Example:
   ```bash
   # .env — temporary
   SECURITY_RATE_LIMIT_CAPACITY=100000
   SECURITY_RATE_LIMIT_REFILL_PER_MINUTE=100000
   docker compose up -d app
   ```
   Restore to `60` afterward. Reused-auth k6 at 15 VUs can exceed 60/min and return 429.
4. Seed a book with large stock:
   ```bash
   chmod +x scripts/seed-loadtest.sh
   ./scripts/seed-loadtest.sh
   curl -s 'http://localhost:8080/api/books' | head
   ```
5. Install k6: `brew install k6`

## One-shot smoke (no k6)

```bash
./scripts/e2e-smoke.sh
```

## Run k6

```bash
# short smoke (defaults: 3 VUs, 30s) — auth once per VU
k6 run scripts/e2e-checkout.js

# sustained checkout load (recommended)
k6 run --vus 15 --duration 5m scripts/e2e-checkout.js

# include BCrypt register+login every iteration
AUTH_EVERY_ITER=true k6 run --vus 10 --duration 1m scripts/e2e-checkout.js

# overrides
BASE_URL=http://localhost:8080 WEBHOOK_SECRET=dev-webhook-secret \
  k6 run --vus 10 --duration 2m scripts/e2e-checkout.js
```

Open Grafana (`http://localhost:3001`) → **Bookstore Overview**. You should see:

- HTTP rate on `/api/auth/*`, `/api/cart/*`, `/api/orders/*`, `/api/payments/*`
- Custom counters: checkouts, payment intents, webhooks

## Switch back to Paystack

In `.env`:

```bash
PAYMENT_PROVIDER=paystack
```

Then `docker compose up -d app` (no full rebuild required for env-only changes).

## Catalog-only load (Apache Bench)

```bash
ab -n 10000 -c 100 http://localhost:8080/api/books
```
