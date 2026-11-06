# API reference

The live, always-current reference is **Swagger UI**: http://localhost:8080/swagger-ui.html.
Its "Authorize" button takes a JWT. This page covers the conventions that Swagger doesn't explain.

A ready-to-run **Postman collection** of all 46 requests, in journey order, is in [`postman/`](../postman).

## Conventions

| Topic | Rule |
|---|---|
| Base path | `/api/v1`. A breaking change becomes `/api/v2`, so old clients keep working. |
| Format | JSON in and out. **Unknown fields are rejected** (400), so a typo like `"quantiy"` fails loudly instead of being ignored. |
| Auth | `Authorization: Bearer <accessToken>` from `/auth/login` or `/auth/register`. The token is valid for 2 h and there is no refresh token. Logout = the client deletes the token. |
| Roles | `CUSTOMER`: cart, orders, payments. `ADMIN`: `/admin/**`. Public: auth, `GET /products/**`, `GET /categories`. |
| Money | Decimal numbers in rupees (`1250.00`). Exception: `payments/initiate` returns `amount` in **paise** (integer), because that's what the gateway wants. |
| Time | ISO-8601 UTC instants (`2026-11-06T10:15:30Z`). The UI converts them to IST. Business days in reports are IST. |
| Paging | `?page=0&size=12` (zero-based, max size 50). Response: `{content, page, size, totalElements, totalPages, first, last}`. |
| Tracing | Every response has `X-Request-Id`. Send your own (8–64 chars `[A-Za-z0-9-]`) to correlate. It also appears in every server log line and in error bodies. |
| Idempotency | `POST /orders` accepts `Idempotency-Key: <uuid>`. Re-sending the same key returns the **same** order with `200` instead of `201`, so a double-click never places two orders. |

## Error format

Every error from every endpoint has this shape (`ApiError`):

```json
{
  "timestamp": "2026-11-06T10:15:30Z",
  "status": 409,
  "error": "Conflict",
  "code": "INSUFFICIENT_STOCK",
  "message": "Only 2 units of 'Wireless Mouse' are available",
  "path": "/api/v1/cart/items",
  "fieldErrors": [],
  "requestId": "4f9c2a1b"
}
```

- `code` is stable. **Program against it.**
- `message` is for humans and may change.
- `fieldErrors` is filled for validation errors: `[{"field": "email", "message": "must be a well-formed email address"}]`.

| HTTP | `code` | Typical cause |
|---|---|---|
| 400 | `VALIDATION_FAILED` | A field broke a rule (see `fieldErrors`) |
| 400 | `MALFORMED_REQUEST` | Bad JSON, unknown field, wrong type, bad header or query parameter |
| 400 | `INVALID_TOKEN` | Password-reset token expired, used or unknown |
| 400 | `PAYMENT_VERIFICATION_FAILED` | Gateway signature doesn't match: forged or tampered |
| 401 | `UNAUTHORIZED` | No token, expired or invalid token, or the account was disabled since the token was issued |
| 401 | `INVALID_CREDENTIALS` | Wrong email or password (same message for both, so nobody can probe which emails exist) |
| 403 | `FORBIDDEN` | Logged in, but wrong role (customer calling `/admin/**`) |
| 403 | `ACCOUNT_DISABLED` | Login attempt on a disabled account |
| 404 | `RESOURCE_NOT_FOUND` | Unknown product, order, address… (also returned for *someone else's* order, so it doesn't reveal that it exists) |
| 404 | `ENDPOINT_NOT_FOUND` | No such URL |
| 405 | `METHOD_NOT_ALLOWED` | e.g. `DELETE /products/1` |
| 409 | `DUPLICATE_RESOURCE` | Email, SKU or category name already taken |
| 409 | `INSUFFICIENT_STOCK` | More requested than available |
| 409 | `INVALID_STATE` | Action not allowed now: illegal status change, cancelling a shipped order, paying a cancelled order |
| 409 | `PAYMENT_LATE` | Payment arrived after the order expired. It is recorded and refunded automatically. |
| 409 | `CONCURRENT_MODIFICATION` | Someone else changed the same row at the same moment. Retry. |
| 409 | `DATA_INTEGRITY_VIOLATION` | A database rule caught something the validation missed |
| 502 | `PAYMENT_GATEWAY_ERROR` | Razorpay unreachable or returned an error |
| 500 | `INTERNAL_ERROR` | A bug. The message is generic; details are in the server log under the `requestId`. |

## Endpoints

| Method | Path | Access | Purpose |
|---|---|---|---|
| POST | `/auth/register` | Public | Create a customer account, returns JWT |
| POST | `/auth/login` | Public | Log in, returns JWT |
| POST | `/auth/forgot-password` | Public | Email a reset link (always 200) |
| POST | `/auth/reset-password` | Public | Set a new password with the emailed token |
| GET | `/users/me` | Logged in | Profile and saved addresses |
| PUT | `/users/me` | Logged in | Update name and phone |
| POST | `/users/me/addresses` | Logged in | Save an address |
| GET | `/products` | Public | Search `q`, filter `category` `minPrice` `maxPrice` `inStock`, sort `relevance` `newest` `price_asc` `price_desc` `popular`, paging |
| GET | `/products/{slug}` | Public | Product details |
| GET | `/categories` | Public | Active categories with product counts |
| GET | `/cart` | Customer | Cart with live prices, totals, shipping and stock warnings |
| POST | `/cart/items` | Customer | Add a product `{"productId": 1, "quantity": 2}` |
| PATCH | `/cart/items/{itemId}` | Customer | Set a line's quantity |
| DELETE | `/cart/items/{itemId}` | Customer | Remove a line |
| POST | `/orders` | Customer | Checkout `{"addressId": 1}` + `Idempotency-Key` |
| GET | `/orders` | Customer | Order history, newest first |
| GET | `/orders/{orderNumber}` | Customer | Order details and timeline |
| PATCH | `/orders/{orderNumber}/cancel` | Customer | Cancel while PLACED or CONFIRMED. Stock is returned; a paid order is refunded. |
| POST | `/payments/{orderNumber}/initiate` | Customer | Create the gateway order, returns the checkout popup options |
| POST | `/payments/verify` | Customer | Verify the signature, confirm the order |
| POST | `/payments/fake/pay` | Customer | **Fake gateway only**: simulate the popup |
| GET | `/admin/dashboard?days=30` | Admin | KPIs, daily revenue and best sellers for 7–90 days, orders by status, low stock, recent orders |
| GET | `/admin/products` | Admin | All products incl. inactive; `lowStock=true` |
| GET | `/admin/products/{id}` | Admin | One product for the edit form |
| POST | `/admin/products` | Admin | Create a product with initial stock and image URLs |
| PUT | `/admin/products/{id}` | Admin | Update; `active=false` hides it (soft delete) |
| POST | `/admin/categories` | Admin | Create a category |
| PATCH | `/admin/inventory/{productId}` | Admin | Relative stock change `{"delta": 50, "reason": "..."}` |
| GET | `/admin/orders?status=&q=` | Admin | Search orders by status / order number / customer |
| GET | `/admin/orders/{orderNumber}` | Admin | Order + customer + payment attempts + allowed next statuses |
| PATCH | `/admin/orders/{orderNumber}/status` | Admin | `SHIPPED`, `DELIVERED` or `CANCELLED` (refunds if paid) |
| GET | `/admin/customers?q=` | Admin | Customers with order count and total spent |
| PATCH | `/admin/customers/{id}/status` | Admin | Enable / disable `{"enabled": false}`. Takes effect on the next request. |

Also available: `GET /actuator/health` (public) and `GET /v3/api-docs` (the OpenAPI JSON).

## Order lifecycle

```
PLACED ──pay──▶ CONFIRMED ──admin──▶ SHIPPED ──admin──▶ DELIVERED
  │                 │
  │ customer /      │ customer / admin
  │ 30-min expiry   │ (refund)
  ▼                 ▼
CANCELLED ◀─────────┘
```

Only a verified payment moves an order to CONFIRMED. Nobody can set it by hand, not even an admin.
