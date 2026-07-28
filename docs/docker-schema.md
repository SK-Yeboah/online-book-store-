# Docker / Dev Schema (Flyway)

Flyway owns the MySQL schema in **dev**, **Docker**, and **prod**. Hibernate uses `ddl-auto=validate` only — it will not alter MySQL `ENUM` columns.

Payment-related statuses (`PENDING_PAYMENT`, `PAYMENT_FAILED`) are applied by:

`src/main/resources/db/migration/V7__create_payments_and_extend_order_status.sql`

## Fresh Docker volume

```bash
docker compose down -v   # wipes mysql_data
docker compose up -d --build
```

On startup the app runs Flyway V1–V8 and creates the correct `orders.status` ENUM.

## Existing volume created under Hibernate `ddl-auto=update`

If you see:

```text
Data truncated for column 'status' at row 1
```

when checking out, the ENUM is missing `PENDING_PAYMENT`. Either reset the volume (above) or run once:

```sql
ALTER TABLE `orders`
    MODIFY COLUMN `status` ENUM(
        'PENDING',
        'PENDING_PAYMENT',
        'PAYMENT_FAILED',
        'CONFIRMED',
        'SHIPPED',
        'DELIVERED',
        'CANCELLED'
    ) NOT NULL DEFAULT 'PENDING';
```

Then ensure Flyway history is aligned (fresh volume is simplest).

## Production secrets

Inject these into the ECS task / Secrets Manager (see `application-prod.properties`):

| Variable | Purpose |
|----------|---------|
| `PAYSTACK_SECRET_KEY` | Paystack secret key |
| `PAYSTACK_PUBLIC_KEY` | Paystack public key |
| `PAYMENT_CALLBACK_URL` | Frontend redirect after payment |
| `PAYMENT_PROVIDER` | Optional; default `paystack` |
| `PAYMENT_CURRENCY` | Optional; default `GHS` |

## Unpaid order expiration

`UnpaidOrderExpirationJob` runs on a fixed delay (`payment.expiration-fixed-delay-ms`, default 5 minutes) when `payment.expiration-enabled=true`.

For each `PENDING_PAYMENT` order older than `payment.intent-ttl-minutes` (default 45):

1. Call the provider `confirmPayment(reference)` if an active payment exists.
2. If paid → mark payment `SUCCEEDED` and order `CONFIRMED` (webhook fallback).
3. If not paid → payment `CANCELLED`, order `CANCELLED`, stock restored.
4. If the provider call fails → **skip** (do not release stock).
5. If there is no payment row → cancel order and restore stock.
