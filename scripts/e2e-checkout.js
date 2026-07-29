/**
 * End-to-end checkout load test (k6).
 *
 * Auth strategy (default): each VU registers + logs in ONCE, then reuses the
 * access token for all iterations. This measures checkout/payment latency
 * without paying BCrypt cost every loop.
 *
 * Set AUTH_EVERY_ITER=true to restore register+login per iteration (auth stress).
 *
 * Flow per iteration (after auth):
 *   list books → add to cart → checkout
 *   → payment intent (mock) → webhook confirm → get order
 *
 * Prerequisites:
 *   1. App running with PAYMENT_PROVIDER=mock (Compose default)
 *   2. Seed book:  ./scripts/seed-loadtest.sh
 *   3. brew install k6
 *
 * Run:
 *   k6 run scripts/e2e-checkout.js
 *   k6 run --vus 15 --duration 5m scripts/e2e-checkout.js
 *   AUTH_EVERY_ITER=true k6 run --vus 10 --duration 1m scripts/e2e-checkout.js
 *
 * Env overrides:
 *   BASE_URL=http://localhost:8080
 *   WEBHOOK_SECRET=dev-webhook-secret
 *   AUTH_EVERY_ITER=true|false (default false)
 */
import http from 'k6/http';
import { check, group, sleep } from 'k6';
import { Trend, Counter } from 'k6/metrics';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const WEBHOOK_SECRET = __ENV.WEBHOOK_SECRET || 'dev-webhook-secret';
const AUTH_EVERY_ITER = String(__ENV.AUTH_EVERY_ITER || 'false').toLowerCase() === 'true';

const e2eDuration = new Trend('e2e_checkout_duration', true);
const e2eSuccess = new Counter('e2e_checkout_success');
const e2eFailure = new Counter('e2e_checkout_failure');

export const options = {
  vus: 3,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.05'],
    e2e_checkout_duration: ['p(95)<5000'],
    checks: ['rate>0.95'],
  },
};

/** Per-VU state (each VU has its own JS isolate). */
let vuToken = null;
let vuBookId = null;
let vuUsername = null;
const VU_PASSWORD = 'password1234';

function jsonHeaders(token) {
  const headers = { 'Content-Type': 'application/json', Accept: 'application/json' };
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  return headers;
}

function registerAndLogin(suffix) {
  const username = `lt${suffix}`.slice(0, 30);
  const email = `lt_${suffix}@example.com`;

  const registerRes = http.post(
    `${BASE_URL}/api/auth/register`,
    JSON.stringify({ username, email, password: VU_PASSWORD }),
    { headers: jsonHeaders() },
  );
  const registered = check(registerRes, {
    'register 201': (r) => r.status === 201,
  });

  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username, password: VU_PASSWORD }),
    { headers: jsonHeaders() },
  );
  const token = loginRes.json('accessToken');
  const loggedIn = check(loginRes, {
    'login 200': (r) => r.status === 200,
    'login has token': () => !!token,
  });

  return {
    ok: registered && loggedIn && !!token,
    username,
    token,
  };
}

function loginOnly(username) {
  const loginRes = http.post(
    `${BASE_URL}/api/auth/login`,
    JSON.stringify({ username, password: VU_PASSWORD }),
    { headers: jsonHeaders() },
  );
  const token = loginRes.json('accessToken');
  const ok = check(loginRes, {
    're-login 200': (r) => r.status === 200,
    're-login has token': () => !!token,
  });
  return { ok: ok && !!token, token };
}

function ensureAuth(forceNew) {
  if (!forceNew && vuToken) {
    return true;
  }

  const suffix = `v${__VU}_${String(Date.now()).slice(-8)}`;
  const auth = registerAndLogin(suffix);
  if (!auth.ok) {
    vuToken = null;
    return false;
  }
  vuToken = auth.token;
  vuUsername = auth.username;
  return true;
}

function resolveBookId(token) {
  if (vuBookId) {
    return vuBookId;
  }
  const booksRes = http.get(`${BASE_URL}/api/books?size=5`, {
    headers: jsonHeaders(token),
  });
  const books = booksRes.json('content') || [];
  const bookId = books.length > 0 ? books[0].id : null;
  const ok = check(booksRes, {
    'books 200': (r) => r.status === 200,
    'books has stock': () => bookId != null,
  });
  if (ok && bookId != null) {
    vuBookId = bookId;
  }
  return bookId;
}

export default function () {
  const started = Date.now();
  const suffix = `${__VU}_${__ITER}_${String(Date.now()).slice(-6)}`;
  let ok = true;

  group('e2e checkout', () => {
    if (AUTH_EVERY_ITER) {
      ok = ensureAuth(true) && ok;
    } else {
      ok = ensureAuth(false) && ok;
    }
    if (!ok || !vuToken) {
      e2eFailure.add(1);
      return;
    }

    const bookId = resolveBookId(vuToken);
    if (!bookId) {
      e2eFailure.add(1);
      return;
    }

    const cartRes = http.post(
      `${BASE_URL}/api/cart/items`,
      JSON.stringify({ bookId, quantity: 1 }),
      { headers: jsonHeaders(vuToken) },
    );
    // Token may expire on long soaks — refresh once and retry cart.
    if (cartRes.status === 401 && vuUsername && !AUTH_EVERY_ITER) {
      const refreshed = loginOnly(vuUsername);
      if (refreshed.ok) {
        vuToken = refreshed.token;
        const retry = http.post(
          `${BASE_URL}/api/cart/items`,
          JSON.stringify({ bookId, quantity: 1 }),
          { headers: jsonHeaders(vuToken) },
        );
        ok =
          check(retry, {
            'add cart 201': (r) => r.status === 201,
          }) && ok;
      } else {
        ok = false;
      }
    } else {
      ok =
        check(cartRes, {
          'add cart 201': (r) => r.status === 201,
        }) && ok;
    }

    const checkoutRes = http.post(`${BASE_URL}/api/orders/checkout`, null, {
      headers: jsonHeaders(vuToken),
    });
    const orderId = checkoutRes.json('orderId');
    ok =
      check(checkoutRes, {
        'checkout 201': (r) => r.status === 201,
        'checkout orderId': () => !!orderId,
      }) && ok;
    if (!orderId) {
      e2eFailure.add(1);
      return;
    }

    const idempotencyKey = `k6_${suffix}`;
    const intentRes = http.post(
      `${BASE_URL}/api/payments/intent`,
      JSON.stringify({ orderId, provider: 'mock' }),
      {
        headers: {
          ...jsonHeaders(vuToken),
          'Idempotency-Key': idempotencyKey,
        },
      },
    );
    ok =
      check(intentRes, {
        'intent 200': (r) => r.status === 200,
      }) && ok;

    const webhookRes = http.post(
      `${BASE_URL}/api/payments/webhook/mock`,
      JSON.stringify({
        reference: idempotencyKey,
        success: true,
        providerPaymentId: `mock_evt_${suffix}`,
        eventId: 'mock.charge.success',
      }),
      {
        headers: {
          'Content-Type': 'application/json',
          'X-Webhook-Secret': WEBHOOK_SECRET,
        },
      },
    );
    ok =
      check(webhookRes, {
        'webhook 200': (r) => r.status === 200,
      }) && ok;

    const orderRes = http.get(`${BASE_URL}/api/orders/${orderId}`, {
      headers: jsonHeaders(vuToken),
    });
    ok =
      check(orderRes, {
        'order 200': (r) => r.status === 200,
        'order CONFIRMED': (r) => r.json('status') === 'CONFIRMED',
      }) && ok;
  });

  e2eDuration.add(Date.now() - started);
  if (ok) {
    e2eSuccess.add(1);
  } else {
    e2eFailure.add(1);
  }
  sleep(0.3);
}
