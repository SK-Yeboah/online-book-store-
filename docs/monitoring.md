# Local monitoring (Prometheus + Grafana)

The app already exposes Micrometer metrics at `/actuator/prometheus`.
Docker Compose runs Prometheus (scrape) and Grafana (dashboards) alongside MySQL, Redis, and the app.

## Start

```bash
docker compose up -d
```

| Service    | URL |
|------------|-----|
| App metrics | http://localhost:8080/actuator/prometheus |
| Prometheus | http://localhost:9090 |
| Grafana    | http://localhost:3001 (default `admin` / `admin`) |

In **prod**, `/actuator/prometheus` requires an **ADMIN** JWT (`security.actuator.prometheus-public=false`).
Keep scrapers on a private network or use a short-lived admin token — never expose metrics on the public ALB without auth.

Override Grafana credentials via `.env`:

```bash
GRAFANA_ADMIN_USER=admin
GRAFANA_ADMIN_PASSWORD=change-me
```

## Verify scrape

1. Open Prometheus → **Status → Targets** → `bookstore-app` should be **UP**.
2. Query `up{job="bookstore-app"}` → `1`.
3. Open Grafana → **Dashboards → Bookstore → Bookstore Overview**.
4. For p95 latency, HTTP timers must publish histograms (enabled via
   `management.metrics.distribution.percentiles-histogram.http.server.requests=true`).
   Query: `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[1m])) by (le, uri))`

## Custom business metrics

| Metric | When incremented |
|--------|------------------|
| `bookstore_orders_checkout_total` | Successful checkout |
| `bookstore_payments_intent_created_total{provider}` | New payment intent |
| `bookstore_payments_webhook_total{provider,result}` | Webhook applied (success/failure) |
| `bookstore_orders_expired_unpaid_total` | Unpaid order expired by job |
| `bookstore_payments_refund_total{provider}` | Admin refund completed |

Instrumentation lives in `BookstoreMetrics` and is called from `OrderServiceImpl` / `PaymentServiceImpl`.

End-to-end load testing (checkout + mock payment): see [load-testing.md](./load-testing.md).

## Files

- `monitoring/prometheus.yml` — scrape config (`app:8080`)
- `monitoring/grafana/provisioning/` — datasource + dashboard provider
- `monitoring/grafana/dashboards/bookstore.json` — overview dashboard
