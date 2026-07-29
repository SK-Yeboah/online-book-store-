#!/usr/bin/env bash
# One-shot end-to-end smoke (no k6). Useful before a load run.
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
WEBHOOK_SECRET="${WEBHOOK_SECRET:-dev-webhook-secret}"
SUFFIX="$(date +%s)_$RANDOM"
USERNAME="sm${SUFFIX}"
USERNAME="${USERNAME:0:30}"
EMAIL="sm_${SUFFIX}@example.com"
PASSWORD="password1234"

echo "== register =="
curl -sf -X POST "$BASE_URL/api/auth/register" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" >/dev/null
echo "ok ($USERNAME)"

echo "== login =="
TOKEN=$(curl -sf -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" | python3 -c 'import sys,json; print(json.load(sys.stdin)["accessToken"])')
echo "ok"

echo "== books =="
BOOK_ID=$(curl -sf "$BASE_URL/api/books?size=1" | python3 -c 'import sys,json; c=json.load(sys.stdin)["content"]; assert c, "no books — run ./scripts/seed-loadtest.sh"; print(c[0]["id"])')
echo "bookId=$BOOK_ID"

echo "== add to cart =="
curl -sf -X POST "$BASE_URL/api/cart/items" \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d "{\"bookId\":$BOOK_ID,\"quantity\":1}" >/dev/null
echo "ok"

echo "== checkout =="
ORDER_ID=$(curl -sf -X POST "$BASE_URL/api/orders/checkout" \
  -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json; print(json.load(sys.stdin)["orderId"])')
echo "orderId=$ORDER_ID"

KEY="smoke_$SUFFIX"
echo "== payment intent =="
curl -sf -X POST "$BASE_URL/api/payments/intent" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $KEY" \
  -H 'Content-Type: application/json' \
  -d "{\"orderId\":$ORDER_ID,\"provider\":\"mock\"}" >/dev/null
echo "ok ($KEY)"

echo "== webhook =="
curl -sf -X POST "$BASE_URL/api/payments/webhook/mock" \
  -H "Content-Type: application/json" \
  -H "X-Webhook-Secret: $WEBHOOK_SECRET" \
  -d "{\"reference\":\"$KEY\",\"success\":true,\"providerPaymentId\":\"mock_evt_$SUFFIX\",\"eventId\":\"mock.charge.success\"}" >/dev/null
echo "ok"

echo "== order status =="
STATUS=$(curl -sf "$BASE_URL/api/orders/$ORDER_ID" \
  -H "Authorization: Bearer $TOKEN" | python3 -c 'import sys,json; print(json.load(sys.stdin)["status"])')
echo "status=$STATUS"
[[ "$STATUS" == "CONFIRMED" ]] || { echo "expected CONFIRMED"; exit 1; }
echo "E2E smoke passed"
