#!/usr/bin/env bash
# Seed a high-stock book into the Docker MySQL used by docker compose.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# shellcheck disable=SC1091
if [[ -f .env ]]; then
  set -a
  # shellcheck disable=SC1090
  source .env
  set +a
fi

DB_NAME="${DB_NAME:-online_book_store}"
DB_PASSWORD="${DB_PASSWORD:-root@123}"

echo "Seeding load-test book into MySQL (${DB_NAME})..."
docker exec -i bookstore-mysql \
  mysql -uroot -p"${DB_PASSWORD}" "${DB_NAME}" < scripts/seed-loadtest-book.sql

echo "Done. Verify with: curl -s http://localhost:8080/api/books | head"
