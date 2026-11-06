#!/usr/bin/env node
/**
 * NorthCart end-to-end smoke test.
 *
 * Drives the REAL running backend through a complete customer + admin journey over HTTP,
 * the same way the React app does, and checks every response. It has no dependencies
 * (Node 18+ has fetch built in).
 *
 *   node scripts/smoke-test.mjs                       # against http://localhost:8080
 *   BASE_URL=https://api.example.com node scripts/smoke-test.mjs
 *
 * Needs: the backend running with the FAKE payment provider (the default in dev),
 * and the admin account from application-dev.yml (override with ADMIN_EMAIL / ADMIN_PASSWORD).
 *
 * Creates its own category, product and customer with unique names, so it can be run any
 * number of times and never depends on the sample catalog. Exit code 0 = all passed.
 */

const BASE_URL = (process.env.BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '');
const ADMIN_EMAIL = process.env.ADMIN_EMAIL ?? 'admin@northcart.local';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD ?? 'Admin12345';

const RUN = Date.now().toString(36);          // unique suffix for this run
const START_STOCK = 5;

// ------------------------------------------------------------------ tiny test harness

const color = (code) => (s) => (process.stdout.isTTY ? `\x1b[${code}m${s}\x1b[0m` : s);
const green = color(32), red = color(31), dim = color(2), bold = color(1);

let passed = 0;

async function step(name, fn) {
  const started = performance.now();
  try {
    const detail = await fn();
    passed++;
    const ms = Math.round(performance.now() - started);
    console.log(`${green('✓')} ${name} ${dim(`${ms} ms${detail ? ` · ${detail}` : ''}`)}`);
  } catch (err) {
    console.log(`${red('✗')} ${name}`);
    console.log(red(`  ${err.message}`));
    if (err.body) console.log(dim(`  response: ${JSON.stringify(err.body).slice(0, 500)}`));
    console.log(`\n${red(bold('SMOKE TEST FAILED'))} after ${passed} passing step(s).`);
    process.exit(1);
  }
}

function check(condition, message, body) {
  if (!condition) {
    const err = new Error(message);
    err.body = body;
    throw err;
  }
}

/** fetch wrapper: JSON in/out, bearer token, and the expected status checked for you. */
async function call(method, path, { token, body, headers = {}, expect = 200 } = {}) {
  let res;
  try {
    res = await fetch(BASE_URL + path, {
      method,
      headers: {
        Accept: 'application/json',
        ...(body !== undefined && { 'Content-Type': 'application/json' }),
        ...(token && { Authorization: `Bearer ${token}` }),
        ...headers,
      },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch (err) {
    throw new Error(`Cannot reach ${BASE_URL} (${err.cause?.code ?? err.message}). Is the backend running?`);
  }
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = text; }

  const expected = Array.isArray(expect) ? expect : [expect];
  check(expected.includes(res.status),
    `${method} ${path} -> HTTP ${res.status}, expected ${expected.join(' or ')}`, json);
  check(res.headers.get('x-request-id'), `${method} ${path} has no X-Request-Id header`);
  return { status: res.status, json, headers: res.headers };
}

const money = (v) => Number(v).toFixed(2);

// ------------------------------------------------------------------ the journey

console.log(bold(`\nNorthCart smoke test → ${BASE_URL}\n`));

const ctx = {};

await step('Backend is up (actuator health)', async () => {
  const { json } = await call('GET', '/actuator/health');
  check(json.status === 'UP', `health is ${json.status}`, json);
});

// ---- admin prepares a product we fully control
await step('Admin logs in', async () => {
  const { json } = await call('POST', '/api/v1/auth/login', { body: { email: ADMIN_EMAIL, password: ADMIN_PASSWORD } });
  check(json.user.roles.includes('ADMIN'), 'logged-in user is not an admin', json);
  ctx.admin = json.accessToken;
  return ADMIN_EMAIL;
});

await step('Admin creates a category', async () => {
  const { json } = await call('POST', '/api/v1/admin/categories', {
    token: ctx.admin, expect: 201, body: { name: `Smoke ${RUN}`, description: 'Created by the smoke test' },
  });
  ctx.categoryId = json.id;
  ctx.categorySlug = json.slug;
  return json.slug;
});

await step(`Admin creates a product with ${START_STOCK} in stock`, async () => {
  const { json } = await call('POST', '/api/v1/admin/products', {
    token: ctx.admin, expect: 201,
    body: {
      name: `Smoketest Lamp ${RUN}`, sku: `smoke-${RUN}`, description: 'Warm desk lamp made for testing.',
      price: 1250.0, categoryId: ctx.categoryId, initialStock: START_STOCK, lowStockThreshold: 2, active: true,
    },
  });
  check(json.sku === `SMOKE-${RUN}`.toUpperCase(), `SKU should be upper-cased, got ${json.sku}`, json);
  check(json.availableQuantity === START_STOCK, 'initial stock not applied', json);
  Object.assign(ctx, { productId: json.id, productSlug: json.slug });
  return `id ${json.id}`;
});

// ---- a visitor browses (no login)
await step('Visitor finds it with a partial-word search', async () => {
  const { json } = await call('GET', `/api/v1/products?q=smoketes&category=${ctx.categorySlug}`);
  check(json.content.some((p) => p.id === ctx.productId), 'product not in search results', json);
  return `${json.totalElements} result(s)`;
});

await step('Visitor opens the product page', async () => {
  const { json } = await call('GET', `/api/v1/products/${ctx.productSlug}`);
  check(money(json.price) === '1250.00', `price is ${json.price}`, json);
});

await step('Unknown product returns the standard error format', async () => {
  const { json, headers } = await call('GET', '/api/v1/products/does-not-exist-' + RUN, { expect: 404 });
  check(json.code && json.message && json.requestId, 'error body is missing code/message/requestId', json);
  check(json.requestId === headers.get('x-request-id'), 'requestId in body differs from the header', json);
});

// ---- a customer signs up and shops
await step('Customer registers', async () => {
  const email = `smoke.${RUN}@example.com`;
  const { json } = await call('POST', '/api/v1/auth/register', {
    expect: 201, body: { fullName: 'Khushi Kumar', email, password: 'Smoke12345', phone: '9876543210' },
  });
  check(json.accessToken && json.user.roles.includes('CUSTOMER'), 'no token or wrong role', json);
  ctx.customer = json.accessToken;
  return email;
});

await step('Security: no token → 401, customer on admin API → 403', async () => {
  await call('GET', '/api/v1/cart', { expect: 401 });
  await call('GET', '/api/v1/admin/dashboard', { token: ctx.customer, expect: 403 });
});

await step('Customer saves a delivery address', async () => {
  const { json } = await call('POST', '/api/v1/users/me/addresses', {
    token: ctx.customer, expect: 201,
    body: { fullName: 'Khushi Kumar', phone: '9876543210', line1: '12 MG Road', city: 'Bengaluru',
            state: 'Karnataka', postalCode: '560001', country: 'India', makeDefault: true },
  });
  ctx.addressId = json.id;
});

await step('Customer adds 2 lamps to the cart', async () => {
  await call('POST', '/api/v1/cart/items', { token: ctx.customer, expect: [200, 201], body: { productId: ctx.productId, quantity: 2 } });
  const { json } = await call('GET', '/api/v1/cart', { token: ctx.customer });
  check(json.totalQuantity === 2 && json.checkoutReady, 'cart not ready', json);
  check(money(json.subtotal) === '2500.00' && money(json.shippingFee) === '0.00', 'free shipping above 999 not applied', json);
  return `total ₹${money(json.total)}`;
});

await step('Cart refuses more than the stock', async () => {
  await call('POST', '/api/v1/cart/items', { token: ctx.customer, expect: 409, body: { productId: ctx.productId, quantity: 10 } });
});

// ---- checkout, with a double-click
const idemKey = `smoke-${RUN}-checkout`;
await step('Checkout places the order (stock reserved)', async () => {
  const { json } = await call('POST', '/api/v1/orders', {
    token: ctx.customer, expect: 201, headers: { 'Idempotency-Key': idemKey }, body: { addressId: ctx.addressId },
  });
  check(json.status === 'PLACED' && money(json.total) === '2500.00', 'unexpected order', json);
  ctx.orderNumber = json.orderNumber;
  return json.orderNumber;
});

await step('Same Idempotency-Key again → same order, not a second one', async () => {
  const { json } = await call('POST', '/api/v1/orders', {
    token: ctx.customer, expect: 200, headers: { 'Idempotency-Key': idemKey }, body: { addressId: ctx.addressId },
  });
  check(json.orderNumber === ctx.orderNumber, `got a different order ${json.orderNumber}`, json);
});

await step(`Stock went ${START_STOCK} → ${START_STOCK - 2}`, async () => {
  const { json } = await call('GET', `/api/v1/admin/products/${ctx.productId}`, { token: ctx.admin });
  check(json.availableQuantity === START_STOCK - 2, `stock is ${json.availableQuantity}`, json);
});

// ---- payment
await step('Payment is initiated (amount in paise from the server)', async () => {
  const { json } = await call('POST', `/api/v1/payments/${ctx.orderNumber}/initiate`, { token: ctx.customer });
  check(json.provider === 'FAKE', `provider is ${json.provider}; the smoke test needs PAYMENT_PROVIDER=fake`, json);
  check(json.amount === 250000, `amount is ${json.amount}, expected 250000 paise`, json);
  ctx.providerOrderId = json.providerOrderId;
  return json.providerOrderId;
});

await step('Customer "pays" in the fake gateway popup', async () => {
  const { json } = await call('POST', '/api/v1/payments/fake/pay', { token: ctx.customer, body: { providerOrderId: ctx.providerOrderId } });
  check(/^[0-9a-f]{64}$/.test(json.signature), 'no HMAC signature returned', json);
  ctx.callback = json;
});

await step('A forged signature is rejected and the order stays unpaid', async () => {
  const { json } = await call('POST', '/api/v1/payments/verify', {
    token: ctx.customer, expect: 400, body: { ...ctx.callback, signature: '0'.repeat(64) },
  });
  check(json.code === 'PAYMENT_VERIFICATION_FAILED', `code is ${json.code}`, json);
  const order = await call('GET', `/api/v1/orders/${ctx.orderNumber}`, { token: ctx.customer });
  check(order.json.status === 'PLACED', `status is ${order.json.status}`, order.json);
});

await step('The genuine signature confirms the order', async () => {
  const { json } = await call('POST', '/api/v1/payments/verify', { token: ctx.customer, body: ctx.callback });
  check(json.status === 'CONFIRMED' && json.confirmedAt, `status is ${json.status}`, json);
});

await step('Verifying twice is harmless (idempotent)', async () => {
  const { json } = await call('POST', '/api/v1/payments/verify', { token: ctx.customer, body: ctx.callback });
  check(json.status === 'CONFIRMED', `status is ${json.status}`, json);
});

// ---- fulfilment by the admin
await step('Admin sees the paid order with its allowed next steps', async () => {
  const { json } = await call('GET', `/api/v1/admin/orders/${ctx.orderNumber}`, { token: ctx.admin });
  check(json.payments[0]?.status === 'SUCCESS', 'payment not SUCCESS', json);
  check(JSON.stringify(json.allowedTransitions) === '["SHIPPED","CANCELLED"]', `transitions ${json.allowedTransitions}`, json);
});

for (const target of ['SHIPPED', 'DELIVERED']) {
  await step(`Admin marks it ${target}`, async () => {
    const { json } = await call('PATCH', `/api/v1/admin/orders/${ctx.orderNumber}/status`, { token: ctx.admin, body: { status: target } });
    check(json.order.status === target, `status is ${json.order.status}`, json);
  });
}

await step('Illegal transition (DELIVERED → SHIPPED) is refused', async () => {
  await call('PATCH', `/api/v1/admin/orders/${ctx.orderNumber}/status`, { token: ctx.admin, expect: 409, body: { status: 'SHIPPED' } });
});

await step('Customer sees DELIVERED in their order history', async () => {
  const { json } = await call('GET', '/api/v1/orders', { token: ctx.customer });
  const mine = json.content.find((o) => o.orderNumber === ctx.orderNumber);
  check(mine?.status === 'DELIVERED', `status is ${mine?.status}`, json);
});

// ---- the unhappy path: cancel an unpaid order, stock comes back
await step('Second order: 1 lamp, then the customer cancels it', async () => {
  await call('POST', '/api/v1/cart/items', { token: ctx.customer, expect: [200, 201], body: { productId: ctx.productId, quantity: 1 } });
  const placed = await call('POST', '/api/v1/orders', { token: ctx.customer, expect: 201, body: { addressId: ctx.addressId } });
  const before = await call('GET', `/api/v1/admin/products/${ctx.productId}`, { token: ctx.admin });
  check(before.json.availableQuantity === START_STOCK - 3, `stock is ${before.json.availableQuantity}`, before.json);

  const { json } = await call('PATCH', `/api/v1/orders/${placed.json.orderNumber}/cancel`, { token: ctx.customer });
  check(json.status === 'CANCELLED' && json.cancelledAt, `status is ${json.status}`, json);
  return placed.json.orderNumber;
});

await step(`Cancelled stock was released (back to ${START_STOCK - 2})`, async () => {
  const { json } = await call('GET', `/api/v1/admin/products/${ctx.productId}`, { token: ctx.admin });
  check(json.availableQuantity === START_STOCK - 2, `stock is ${json.availableQuantity}`, json);
  check(json.soldCount === 2, `soldCount is ${json.soldCount}, expected 2`, json);
});

await step('Admin dashboard includes the sale', async () => {
  const { json } = await call('GET', '/api/v1/admin/dashboard?days=7', { token: ctx.admin });
  check(json.salesByDay.length === 7, 'expected 7 days of sales', json);
  check(json.topProducts.some((p) => p.productId === ctx.productId) || json.topProducts.length === 5,
    'smoke product missing from best sellers', json.topProducts);
  return `revenue ₹${money(json.summary.revenue)}, ${json.summary.paidOrders} paid orders`;
});

console.log(`\n${green(bold(`ALL ${passed} STEPS PASSED`))}  ${dim(`run id ${RUN}`)}\n`);
