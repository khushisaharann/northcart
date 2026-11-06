# Interview prep: NorthCart

How to use this: read the answer, then **close the file and explain it out loud in your own words**,
pointing at the real class or SQL. Interviewers probe one level deeper than your answer, so every
answer here names the next layer down (the "because").

Contents:

1. [The 2–3 minute pitch](#the-23-minute-pitch)
2. [50 questions about NorthCart](#part-1--50-questions-about-northcart)
3. [20 Spring Boot & Java questions](#part-2--20-spring-boot--java-questions)
4. [15 SQL & database questions](#part-3--15-sql--database-questions)
5. [15 React & JavaScript questions](#part-4--15-react--javascript-questions)
6. [10 architecture & scenario questions](#part-5--10-architecture--scenario-questions)
7. [Numbers to remember](#numbers-to-remember)

---

## The 2–3 minute pitch

> **What it is.** NorthCart is a full-stack e-commerce platform I built end to end: a storefront where
> customers search products, manage a cart, check out and pay, and an admin console to run the shop:
> a revenue dashboard, order fulfilment, products, stock and customers. The backend is Java 21 and
> Spring Boot with PostgreSQL; the frontend is React with Tailwind. It has about thirty REST endpoints
> and fourteen tables, and it runs with one Docker Compose command.
>
> **What I focused on: correctness where money and stock are involved.** Two customers can't buy
> the last unit. Stock is decremented with a single conditional UPDATE, and a CHECK constraint backs it up.
> I proved it with a test where two customers race to check out the last three units: exactly one wins and stock ends at zero. A double-clicked "Place
> order" creates one order, thanks to an idempotency key with a unique constraint. Unpaid orders
> release their stock after 30 minutes through a scheduled job that uses `SKIP LOCKED`, so it's safe to run
> on several servers.
>
> **Payments** go through Razorpay, but the browser is never trusted. It sends back a signature, and the
> server recomputes the HMAC-SHA256 with the secret key before an order becomes CONFIRMED. If a
> payment arrives after the order expired, it's recorded and refunded automatically. For demos there's a
> fake gateway that signs exactly like Razorpay, so the verification code is the same.
>
> **Engineering practices.** Security is stateless JWT with role-based access, and disabling a user locks them out on the next request.
> Every error has one JSON format with a stable code and a request id that matches the server logs.
> Emails go out asynchronously after the transaction commits, so a slow mail server can't break
> checkout. The schema is managed by Flyway migrations, and database constraints enforce the rules.
>
> **Testing and performance.** About 140 backend tests run against a real PostgreSQL through
> Testcontainers, plus React tests and an end-to-end smoke script. I also loaded 200,000 orders and
> used EXPLAIN ANALYZE. I found that the search query wasn't using its full-text index because of
> one OR condition, and that the dashboard scanned every order ever placed. Fixing those made search
> five times faster and the dashboard about three times faster.
>
> **What I'd do next:** a daily sales roll-up table for the dashboard, Razorpay webhooks as a second
> confirmation path, and refresh tokens.

*Delivery tips:* stop after each paragraph; the interviewer will pick a thread. Have the
concurrency test and the EXPLAIN plans ready to show (`CartIntegrationTests`, `docs/performance.md`).

---

## Part 1: 50 questions about NorthCart

### Overview & architecture

**1. Walk me through the architecture.**
A React single-page app calls a Spring Boot REST API under `/api/v1`, and the API stores data in PostgreSQL.
Inside, it's a modular monolith: packages by feature (`auth`, `catalog`, `inventory`, `cart`, `order`,
`payment`, `admin`, `notification`), each with controller → service → repository. Modules call each
other only through services, so `OrderService` calls `InventoryService.reserve()` and never touches the inventory repository.
Cross-cutting pieces live in `common`, `config` and `security`.

**2. Why a monolith and not microservices?**
Checkout must reserve stock, create the order and clear the cart atomically. In one database
that's one `@Transactional` method. With microservices it becomes a distributed saga with compensating
actions. Microservices solve an organisational problem (many teams deploying independently) that this
project doesn't have, and they add network failures, distributed tracing and data duplication. The
feature packages keep the option to split later.

**3. Why package by feature instead of by layer?**
Everything about orders lives in `order/`. A change touches one folder, the module boundary is visible,
and `package-private` can hide internals. With `controllers/ services/ repositories/`, every feature is spread over
every folder.

**4. What happens, end to end, when a customer clicks "Place order"?**
React sends `POST /api/v1/orders` with `{addressId}` and an `Idempotency-Key` header. Axios adds the JWT.
The filter chain runs: `RequestIdFilter` sets the MDC, then CORS, then `JwtAuthenticationFilter` validates the token and re-loads
the user, then the role check. Then `OrderController` → `OrderService.placeOrder`, in one transaction:
1. If the key was seen before, return that order.
2. Lock the cart and validate the address.
3. Reserve stock product by product, in id order.
4. Snapshot prices and the address into the order, save it as PLACED, clear the cart.

After commit, an `OrderPlacedEvent` triggers an async email. The response is 201 with the order.

**5. Why Spring Boot 3.5 and Java 21?**
Java 21 is the current LTS: records for DTOs, pattern matching, text blocks for SQL, virtual
threads available. Spring Boot 3.x is built on Jakarta EE 10 and Spring 6, and gives auto-configuration,
an embedded Tomcat, Actuator and good testing support.

**6. How is configuration handled across environments?**
`application.yml` holds shared settings with `${ENV_VAR}` placeholders. `application-dev.yml` has safe local defaults
(a dev JWT key, a dev admin, the sample catalog). `application-prod.yml` **has no defaults**, so a missing secret makes startup fail
instead of running misconfigured. Business settings are bound to typed `@ConfigurationProperties`
records (`AppProperties`, `PaymentProperties`), validated at startup.

**7. How do the frontend and backend communicate in development and in production?**
In development, Vite's dev server proxies `/api` to `localhost:8080`, so the browser sees one origin. In Docker
and production, nginx serves the React files and reverse-proxies `/api` to the backend container. Either way it's
the same origin, so no CORS preflights. CORS is still configured with exact origins for the case where
the frontend is hosted elsewhere.

**8. What's in the database? Name the main tables.**
Fourteen tables: `users`, `roles`, `user_roles`, `password_reset_tokens`, `addresses`, `categories`, `products`,
`product_images`, `inventory`, `carts`, `cart_items`, `orders`, `order_items`, `payments`. Inventory is a separate 1:1
table so stock updates don't lock or bump the product row that the catalog reads.

### Security

**9. How does authentication work?**
Login checks the password against its BCrypt hash and returns a signed JWT (HS256, 2 hours,
claims: subject = user id, email, roles, issuer). On every request, `JwtAuthenticationFilter` reads `Authorization: Bearer`,
verifies the signature, expiry and issuer, loads the user and puts an `AuthenticatedUser` in the
`SecurityContext`. Sessions are `STATELESS`: nothing is stored on the server.

**10. If JWTs are stateless, how does disabling a user take effect immediately?**
The filter re-loads the user from the database on every request and rejects the request if the account is
disabled. That's one primary-key lookup per request, a deliberate trade: we give up a bit of statelessness for instant
revocation. `AdminIntegrationTests.disablingACustomerBlocksThemImmediately` proves it: the old token
gets a 401 on the very next call.

**11. Why no refresh token? How does logout work?**
For scope: a 2-hour access token keeps the design simple, and logout is client-side (delete the token).
The risk is a stolen token staying valid for up to 2 hours. That's mitigated by HTTPS, the short lifetime, and the disable
check above. The next step would be a short access token plus a rotating refresh token in an httpOnly cookie,
stored hashed server-side so it can be revoked.

**12. Where is the token stored in the browser, and what's the risk?**
In `localStorage`. It's simple and survives refreshes, but any XSS can read it. React escapes output by
default, there's no `dangerouslySetInnerHTML`, and nginx sends `nosniff` and `X-Frame-Options`. An httpOnly
cookie would hide the token from JavaScript, but then CSRF protection is needed. That's the trade-off.

**13. Why is CSRF protection disabled?**
CSRF exploits cookies that the browser attaches automatically. NorthCart authenticates with a header the
browser never adds by itself, so a malicious site can't make an authenticated request. If I moved
the token into a cookie, I'd turn CSRF protection back on.

**14. How are passwords stored?**
BCrypt with strength 12: salted, deliberately slow, never plaintext. The login error is the same for
"no such email" and "wrong password" (`INVALID_CREDENTIALS`), so attackers can't find out which emails exist.

**15. How does password reset work securely?**
`forgot-password` always answers 200. If the account exists, it generates a random token, emails the
raw token and stores only its **SHA-256 hash**, with a 15-minute expiry and `used_at`. `reset-password` hashes
the submitted token, finds it, and checks that it's unused and unexpired. A database leak doesn't reveal usable tokens.
Why SHA-256 and not BCrypt here: the token is long and random, so a fast hash is enough, and the lookup must be by
hash.

**16. How is authorization enforced?**
URL rules in `SecurityConfig`:
- `/auth/**` and `GET /products|categories/**` are public;
- `/admin/**` needs `ADMIN`;
- cart, orders and payments need `CUSTOMER`.

Ownership is enforced in the queries: `findByOrderNumberAndUserId`, so a customer asking for someone else's order
gets 404 (not 403), which doesn't even confirm that it exists.

### Catalog & search

**17. How does product search work?**
PostgreSQL full-text search. `products.search_vector` is a **generated** tsvector column
(name weighted A, description B) with a GIN index. User input is turned into a prefix query (`"mech keyb"` →
`mech:* & keyb:*`) by keeping only letters and digits, so input can't break the tsquery syntax. Results are ranked
by `ts_rank`. An exact SKU also matches.

**18. Why not `LIKE '%keyboard%'`, or Elasticsearch?**
A leading-wildcard LIKE can't use a B-tree index, so it scans every row. It also has no stemming or ranking.
Elasticsearch is the right tool at millions of products or for typo tolerance, but it's a second system
to run and keep in sync. Postgres FTS gives stemming, ranking and an index with zero new infrastructure.

**19. Why is search built with plain SQL instead of JPA?**
JPQL can't express `tsvector`, `to_tsquery` or `ts_rank`. `ProductSearchRepository` uses
`NamedParameterJdbcTemplate`. Every user value is a bind parameter; the only concatenated parts are fixed
fragments chosen by code (like the ORDER BY from an enum), so there's no injection risk.

**20. How does paging work with products that have a list of images?**
In two steps. Step 1 is the SQL query, which returns just the product **ids** for the page with LIMIT/OFFSET. Step 2 loads those
entities with their images. Fetching a collection together with pagination makes Hibernate paginate in memory
(the HHH90003004 warning), and two steps avoids that. The sort always ends with `p.id DESC` as a tie-breaker, so equal prices
can't swap between pages.

**21. You said one OR made search slow. Explain.**
The filter was `search_vector @@ query OR upper(p.sku) = upper(:text)`. Wrapping the column in `upper()`
makes any index on `sku` unusable. Postgres can only index an OR when **both** sides are indexable (a BitmapOr), so it
fell back to scanning all 50k products: 18.6 ms. SKUs are already stored upper-case, so I compare
`p.sku = upper(:text)`, use the existing unique index, and enforce the invariant with a CHECK constraint (V5).
It now takes 3.4 ms.

### Cart, inventory & concurrency

**22. How do you prevent overselling?**
One atomic statement (`InventoryRepository.adjustQuantity`, with a negative delta for a reservation):
`UPDATE inventory SET quantity_available = quantity_available + :delta, version = version + 1 WHERE product_id = :id AND quantity_available + :delta >= 0`.
If it updates 0 rows, there wasn't enough stock and the whole checkout rolls back. The row lock makes concurrent decrements
run one after another, and `CHECK (quantity_available >= 0)` is the last line of defence. The version is bumped by hand
because bulk updates bypass Hibernate's `@Version` handling.

**23. Why not read the stock, check it in Java, then save?**
That's a check-then-act race. Two requests both read "1 left", both pass the check, both save 0, and the shop sells two.
Fixing it in Java needs a pessimistic lock or an optimistic retry loop. The conditional UPDATE does the check and
the write atomically, in one round-trip.

**24. How did you test the concurrency?**
`OrderIntegrationTests.twoCustomersRacingForTheLastUnits_neverOversell`: a product with 3 units, and two customers who
each have 3 in their cart (carts only *check* stock, they don't reserve it). Both checkouts run in parallel on an
`ExecutorService`. The assertions: the statuses are exactly `{201, 409}`, and the final stock is 0. Separately,
`CartIntegrationTests.parallelAddsFromSameUser_areSerialised` sends five parallel "add 1" requests from one user and
expects one cart line with quantity 5. I also replayed the race by hand in two `psql` sessions to watch the second
UPDATE wait for the first.

**25. Why reserve products in sorted id order?**
To avoid deadlocks. Cart A has products 1 and 2, cart B has 2 and 1. If each locks in its own order,
A holds 1 and waits for 2 while B holds 2 and waits for 1, and Postgres kills one transaction. If everyone locks in ascending id
order, waits can't form a cycle.

**26. When is stock reserved: at add-to-cart, at checkout, or at payment?**
At checkout (order PLACED). At add-to-cart, people would hoard stock in carts they abandon. At payment, two
customers could both pay for the last unit and one would need a refund. Reserving at checkout plus a
30-minute payment window is the usual compromise.

**27. What stops a cart from being modified twice at the same time?**
The cart row is created race-free with `INSERT INTO carts (user_id) … ON CONFLICT (user_id) DO NOTHING`. Every cart
write then locks that row (`@Lock(PESSIMISTIC_WRITE)`, i.e. `SELECT … FOR UPDATE`), so parallel requests from the same
user run one after another. On top of that, the unique constraint `uk_cart_items_cart_product` makes two lines for one product impossible. The test fires five parallel adds and
gets one line with quantity 5. Without the lock, updates would be lost.

**28. How does the cart show problems such as a price change or an item out of stock?**
`GET /cart` always computes with **current** prices and stock. Each line can carry an `issue`
(`OUT_OF_STOCK`, `INSUFFICIENT_STOCK`, `UNAVAILABLE`) and the cart has a `checkoutReady` flag. Prices aren't
stored in the cart at all; they're frozen only when the order is created.

### Orders

**29. Why copy prices and the address into the order?**
An order is a historical record. If the product price changes or the customer edits their address, past
orders must still show what was actually bought and where it was shipped. So `order_items` stores
`product_name`, `sku`, `unit_price` and `line_total`, and `orders` stores `ship_*`. A CHECK enforces
`line_total = unit_price * quantity`.

**30. How does the idempotency key work?**
The client generates a UUID per checkout attempt and sends it as `Idempotency-Key`. The service first looks
for an order with that `(user_id, key)`; if found, it returns it with **200** instead of 201. The unique constraint
`uk_orders_user_idempotency_key` covers the race where two identical requests arrive at the same moment: one insert wins, and
the other's transaction fails on the constraint. `GlobalExceptionHandler` maps that constraint name to a friendly 409,
"This order has already been submitted". The loser rolls back completely (no stock reserved), and a retry with the same key
returns the order.

**31. How is the order status managed?**
`OrderStatus` is an enum state machine: each status knows its allowed next statuses. The rules:
- `PLACED → CONFIRMED | CANCELLED`
- `CONFIRMED → SHIPPED | CANCELLED`
- `SHIPPED → DELIVERED`

`Order.changeStatus` rejects illegal moves with `INVALID_STATE` (409) and sets `confirmedAt`, `shippedAt` and so on. CONFIRMED can only
come from a verified payment, never from the admin API. The admin UI shows the `allowedTransitions` the API
returns, so the buttons and the rules can't drift apart.

**32. What happens when an order is cancelled?**
In one transaction the stock is released (atomic `+qty`), `sold_count` is reversed and the status becomes CANCELLED with
`cancelled_at`; a CHECK ties those two together. If it was paid, an `OrderCancelledEvent` triggers an
**async refund after commit**. The refund locks the payment row, so two triggers can't refund twice.

**33. How do unpaid orders expire?**
`OrderExpiryScheduler` runs every minute and claims a batch with
`SELECT … WHERE status='PLACED' AND created_at < :cutoff ORDER BY created_at LIMIT 50 FOR UPDATE SKIP LOCKED`.
Before cancelling, it **asks the gateway** whether a payment was captured. If one was, it confirms the order instead. It's
disabled in tests so tests control time.

**34. Why `SKIP LOCKED`?**
With two app instances, both jobs run at the same time. Plain `FOR UPDATE` would make the second wait for the first's
rows. `SKIP LOCKED` makes it take *different* rows instead. Without any lock, both would process the same orders and release
stock twice. It's the standard Postgres "job queue" pattern.

**35. What's a "late payment" and how is it handled?**
The customer pays at minute 29:59, but the order expired at 30:00. The money is real, so `verify` records the payment as
SUCCESS and throws `LatePaymentException`. The method is `@Transactional(noRollbackFor = LatePaymentException.class)`
so the record is committed. Then the payment is refunded automatically and the customer gets a 409 `PAYMENT_LATE`
explaining it.

### Payments

**36. Explain the payment flow.**
1. `initiate`: the server creates a gateway order with the amount **from the database**, in paise, and returns the public key id.
2. The browser opens the Razorpay popup; the customer pays.
3. Razorpay gives the browser `payment_id` and `signature = HMAC_SHA256(order_id + "|" + payment_id, key_secret)`.
4. `verify`: the server recomputes the HMAC with the secret and compares it in constant time. Only then does it
   lock the order and confirm it.

**37. Why can't a user fake a successful payment?**
Faking it needs the key **secret**, which exists only on the server. Every input to `verify` is untrusted:
the amount isn't sent by the client at all, and the payment must belong to that user's order.
`PaymentIntegrationTests.forgedOrTamperedSignatures_areRejected` and the smoke test check that a zero signature, or a
signature combined with someone else's payment id, gets a 400.

**38. Why a constant-time comparison?**
`String.equals` returns at the first different character. By timing many attempts, an attacker
could guess a valid signature byte by byte. `MessageDigest.isEqual` always compares all the bytes.

**39. What's the fake gateway and why build it?**
`FakePaymentGateway` implements the same `PaymentGateway` interface and signs with exactly Razorpay's scheme.
The full flow, including the real `verify` code, runs in tests, demos and CI with no account and no network. It's selected
by `app.payment.provider=fake` with `@ConditionalOnProperty`, and its `/payments/fake/pay` endpoint doesn't exist when
the provider is razorpay.

**40. How do you make sure an order isn't paid twice?**
At three layers:
- `initiate` refuses an order that's already paid.
- `verify` is idempotent: replaying the same callback returns the confirmed order.
- A **partial unique index** `ux_payments_one_success_per_order ON payments(order_id) WHERE status='SUCCESS'` makes two successful
  payments for one order impossible in the database.

**41. Why do you lock and then `refresh` the order in `verify`?**
I fixed a real bug here. The order had been read earlier in the transaction, *then* locked. The lock waited for a
concurrent cancellation, but the code continued with the stale entity it already held. Now `lockOrderById` does
`entityManager.refresh(order, PESSIMISTIC_WRITE)`, which locks and re-reads in one step, so decisions are made on the
latest committed state.

**42. What would you add before taking real money?**
- Razorpay **webhooks** (`payment.captured`) as a second confirmation path, in case the browser closes before `verify`.
- An outbox table, so refunds survive a crash between commit and the async call.
- Reconciliation reports.
- Live keys stored in a secret manager.

### Admin, reporting & operations

**43. How is the dashboard computed?**
In SQL, not Java:
- the summary uses `count(*) FILTER (WHERE …)` in one pass;
- sales per day use `generate_series` with a LEFT JOIN, so days without sales still show 0;
- best sellers come from an aggregate over `order_items`.

It runs `@Transactional(readOnly = true, isolation = REPEATABLE_READ)`, so all queries see the same snapshot
and the tiles can't disagree with the chart.

**44. Why group days in India time?**
A shop's "today" is the business's local day. 00:30 IST on Nov 6 is 19:00 UTC on Nov 5, so grouping by UTC would
put it on the wrong day. The query converts with `AT TIME ZONE 'Asia/Kolkata'`.
`AdminStatsRepositoryTest` pins the edge cases: 23:59:59 IST is the previous day, and both ends of the range are inclusive.

**45. How do you avoid N+1 queries in the admin customer list?**
The page of customers is loaded first. Then **one** query gets the order count and total spent for all of their ids:
`WHERE user_id IN (:ids) GROUP BY user_id`. Elsewhere, `default_batch_fetch_size: 50` batches lazy collections,
and `open-in-view: false` makes accidental lazy loads in controllers fail loudly.

**46. How do you debug a production error a user reports?**
Every response has `X-Request-Id`, which is also in every error body and, through the MDC, in every log line, including
`@Async` threads (a `TaskDecorator` copies the MDC). The user sends the id from the error; I search the logs for it and see
that request's whole story.

**47. How are emails sent without slowing down or breaking checkout?**
Services publish domain events. `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` listeners render
Thymeleaf templates and send them through `JavaMailSender` on a thread pool. If the transaction rolls back, no email
goes out; if SMTP is slow or down, the order is already committed. Locally, Mailpit catches everything.

### Testing & quality

**48. How is the backend tested?**
- **Unit tests** for pure logic: the status machine, the `Order` entity, the JWT service, payment signatures checked against an
  HMAC vector I computed independently, slug generation, and tsquery building.
- **Integration tests** (`@SpringBootTest` + MockMvc) against a **real PostgreSQL in Testcontainers**: auth, catalog, cart
  concurrency, orders, payments, admin.
- **Repository slice tests** (`@DataJpaTest`) for the hand-written SQL.

In total that's about 140 tests.

**49. Why Testcontainers instead of H2?**
H2 doesn't have tsvector, partial indexes, `SKIP LOCKED`, `ON CONFLICT`, or Postgres's exact constraint and
locking behaviour, and the interesting bugs live exactly there. Testcontainers runs the same Postgres 16 as
production. A single shared container and Spring context keep it fast.

**50. What was the hardest bug, and what did you learn?**
The stale-state bug in payment verification (Q41). Taking a lock doesn't refresh an entity that's already loaded,
so the code acted on data from before the lock. What I learned: in JPA, "lock then read" must be one operation
(`refresh` with a lock mode, or a `SELECT … FOR UPDATE` query), and concurrency bugs are found by thinking through
interleavings, not by running the happy path.

---

## Part 2: 20 Spring Boot & Java questions

**1. What does `@SpringBootApplication` do?**
It combines `@Configuration`, `@EnableAutoConfiguration` and `@ComponentScan`. Auto-configuration looks at the
classpath and properties. For example, finding `spring-boot-starter-data-jpa` plus a datasource URL creates a DataSource, an
EntityManagerFactory and a transaction manager, each guarded by `@ConditionalOn…` so your own bean wins.

**2. Dependency injection: why constructor injection?**
Dependencies become `final`, so the object is immutable and always complete. They're visible in the constructor, and tests can
pass mocks without Spring. NorthCart uses Lombok's `@RequiredArgsConstructor` for this. Field injection
(`@Autowired` on a field) hides dependencies and needs reflection to test.

**3. Bean scopes, and the default?**
The default is singleton: one instance per application context. So beans must be stateless or thread-safe, because
every request thread shares them. Others: `prototype` (a new one per injection), and `request` / `session` in web apps.

**4. How does `@Transactional` work, and when does it silently not work?**
Spring wraps the bean in a **proxy** that begins the transaction before the method and commits or rolls back after it.
It doesn't apply in these cases:
- **self-invocation** (`this.otherMethod()` skips the proxy);
- `private` methods;
- checked exceptions (by default only `RuntimeException` and `Error` trigger a rollback);
- the bean wasn't created by Spring.

**5. Propagation: `REQUIRED` vs `REQUIRES_NEW`?**
`REQUIRED` (the default) joins the existing transaction or starts one. `REQUIRES_NEW` suspends the current one and runs
in its own, which commits independently. That's useful for audit logs that must survive a rollback. NorthCart's refunds
run after commit in a fresh transaction.

**6. What are isolation levels, and which did you use where?**
PostgreSQL's default is READ COMMITTED: each statement sees the data committed when *it* started. The dashboard
uses REPEATABLE READ so all its queries see one snapshot. Stock correctness doesn't depend on isolation at all,
because the conditional UPDATE plus row locks handle it.

**7. `@Transactional(readOnly = true)`: what does it actually do?**
Hibernate skips dirty checking and flushing, which is faster and uses less memory. The JDBC connection is marked read-only, a hint
the driver or database may use, for example to route the query to a replica. It documents intent too.

**8. JPA entity states and dirty checking?**
The states are transient (new), managed (attached to the persistence context), detached and removed. For managed entities,
Hibernate compares their state with a snapshot at flush time and issues UPDATEs automatically. That's why
`UserService.updateProfile` has no `save()`.

**9. What is the N+1 problem and how do you fix it?**
One query loads N parents, then accessing a lazy association runs one more query per parent. The fixes:
- `JOIN FETCH` or `@EntityGraph`;
- `default_batch_fetch_size` (it loads collections for up to 50 parents per query);
- DTO projections;
- for paging, an ids-first query.

**10. `FetchType.LAZY` vs `EAGER`?**
LAZY loads on first access; EAGER always loads. EAGER is the default for `@ManyToOne` and is usually wrong:
it loads too much and causes N+1 in lists. NorthCart makes every association LAZY and fetches what each use case needs.

**11. What does `open-in-view: false` change?**
With it on (Spring's default), the Hibernate session stays open until the view renders, so lazy loads can quietly run during JSON serialisation:
hidden queries, outside any transaction. Off means the service must load everything it needs, and mistakes
fail fast with `LazyInitializationException`.

**12. Optimistic vs pessimistic locking?**
- **Optimistic**: a `@Version` column. An update includes `WHERE version = ?`; 0 rows means someone else won, so the caller gets
  `OptimisticLockException` and retries. Good when conflicts are rare.
- **Pessimistic**: `SELECT … FOR UPDATE` blocks other writers. Good when conflicts are likely or a retry is costly.

NorthCart uses a version on inventory, `FOR UPDATE` on carts and on orders during payment, and atomic UPDATEs for stock.

**13. How does Bean Validation integrate with Spring MVC?**
`@Valid` on a `@RequestBody` runs the constraints (`@NotBlank`, `@Email`, `@Pattern`, `@Positive` …) before the method.
Failures throw `MethodArgumentNotValidException`, and `GlobalExceptionHandler` turns them into a 400 with `fieldErrors`.
Query-parameter objects (`@ParameterObject`) raise `BindException`, handled the same way.

**14. How does `@RestControllerAdvice` exception handling work?**
`@ExceptionHandler` methods in one advice class map exception types to responses for every controller. NorthCart has a
`BusinessException` base (status + `ErrorCode`) and a catch-all that logs the stack trace and returns a generic 500.
Security exceptions happen in filters, before controllers, so they're written by the `AuthenticationEntryPoint` /
`AccessDeniedHandler` in the same `ApiError` format.

**15. Explain the Spring Security filter chain.**
It's a list of servlet filters in front of the DispatcherServlet. NorthCart configures a `SecurityFilterChain` bean:
- stateless sessions, CSRF off, CORS on;
- the JWT filter is added before `UsernamePasswordAuthenticationFilter`;
- URL rules, plus custom 401 and 403 handlers.

The authenticated principal lives in a thread-local `SecurityContext`, and `@AuthenticationPrincipal` injects it.

**16. `@Async`: how does it work, and what are the pitfalls?**
A proxy submits the call to a `TaskExecutor` and returns immediately. Pitfalls:
- self-invocation skips the proxy;
- exceptions in `void` methods are only logged;
- thread-locals don't follow: MDC, the security context, the transaction (NorthCart's `TaskDecorator` copies the MDC);
- an unbounded queue can hide overload.

**17. Records: what are they and where did you use them?**
Immutable data carriers: `equals`, `hashCode`, `toString` and accessors are generated, and the fields are final. I used them for DTOs,
`@ConfigurationProperties` and events. Not for JPA entities, which need a no-arg constructor, mutability and proxies.

**18. `equals`/`hashCode` for JPA entities?**
Don't use Lombok `@Data` on entities. The generated equals/hashCode include lazy collections (extra loads, infinite
recursion) and change once the id is assigned, which breaks `HashSet`. A safe pattern: equality by id when it's non-null, and
a constant `hashCode` per class.

**19. `BigDecimal` for money: why, and the gotchas?**
`double` can't represent 0.1 exactly, so sums drift. With `BigDecimal`:
- compare with `compareTo`, not `equals` (`2.0` ≠ `2.00` under `equals`);
- always give a scale and `RoundingMode` when dividing.

In the database the type is `NUMERIC(12,2)`, and amounts go to the gateway as integer paise.

**20. Checked vs unchecked exceptions, and why business exceptions are unchecked here?**
Checked exceptions must be declared or caught; unchecked ones (`RuntimeException`) don't. Business errors bubble up to one handler, and
unchecked exceptions trigger `@Transactional` rollback by default. Declaring them on every signature adds noise for no safety.

---

## Part 3: 15 SQL & database questions

**1. What's an index, and when does it NOT help?**
A separate sorted structure (B-tree by default) that finds rows without a full scan. It doesn't help, or can't be used, when:
- the query matches a large share of the table;
- a function wraps the column (`upper(sku)`, unless you index the expression);
- the pattern has a leading wildcard (`LIKE '%x'`);
- the query doesn't use the index's leftmost column.

Indexes cost write speed and disk space.

**2. Composite index column order: why `(user_id, created_at DESC)`?**
The leftmost column is used for equality, the next for range or sort. "This user's orders, newest first" seeks to
`user_id` and reads entries already in order: no sort, and it stops after 10 rows. `(created_at, user_id)` would be
useless for that query.

**3. What is a partial index? Give your examples.**
An index over only the rows matching a WHERE clause: smaller and faster. NorthCart has three:
- `ux_addresses_one_default_per_user ON addresses(user_id) WHERE is_default` (also unique: at most one default address);
- `ux_payments_one_success_per_order … WHERE status='SUCCESS'`;
- `ix_orders_paid_confirmed_at … WHERE status IN (paid)` for the dashboard.

**4. How do you read `EXPLAIN ANALYZE`?**
Read the tree bottom-up. Per node, compare estimated and actual rows (a big gap means stale statistics, so run `ANALYZE`), check the
actual time, and watch for loops. Red flags in a hot path: Seq Scan on a big table, "Rows Removed by Filter", and
`Sort Method: external merge Disk`. `BUFFERS` shows whether data came from cache or disk.

**5. `WHERE` vs `HAVING`?**
`WHERE` filters rows before grouping; `HAVING` filters groups after aggregation, e.g.
`HAVING sum(total) > 10000`. Put everything you can in `WHERE`, because less data then reaches the aggregate.

**6. What does `count(*) FILTER (WHERE …)` give you?**
Several conditional aggregates in **one** scan:
`count(*) FILTER (WHERE status='CANCELLED')`, `sum(total) FILTER (WHERE status IN …)`. It's cleaner than
`sum(CASE WHEN … THEN 1 ELSE 0 END)` and replaced three subqueries on the dashboard.

**7. Why `generate_series` + LEFT JOIN for daily sales?**
GROUP BY only returns days that *have* orders, so a chart would skip days without sales. Generating every
day and LEFT JOINing the aggregated orders gives zeros for empty days.

**8. INNER vs LEFT JOIN?**
INNER keeps only matching rows on both sides; LEFT keeps every row of the left table, with NULLs where nothing
matches. A common bug: a `WHERE` condition on the right table turns a LEFT JOIN back into an INNER join. Put such
conditions in `ON`.

**9. ACID: explain with checkout.**
- **Atomicity**: stock reservation, order insert and cart clearing all happen or none do.
- **Consistency**: constraints hold after commit (stock ≥ 0, totals add up).
- **Isolation**: concurrent checkouts don't see each other's half-done work.
- **Durability**: once the API returns 201, a crash doesn't lose the order (WAL).

**10. What can go wrong between concurrent transactions?**
- dirty reads (prevented in Postgres at every level);
- non-repeatable reads, and phantoms;
- **lost updates**: two read-modify-writes, where the last one wins;
- write skew.

The lost update is the one e-commerce hits, and atomic UPDATEs or locking prevent it.

**11. What does `SELECT … FOR UPDATE SKIP LOCKED` do?**
It locks the selected rows, *skipping* rows already locked by others instead of waiting. Several workers can then
take disjoint batches from the same "queue" table. NorthCart's expiry job uses it.

**12. What is `INSERT … ON CONFLICT`?**
An upsert. If a unique constraint would be violated, it either `DO NOTHING` or `DO UPDATE SET …`, atomically.
It's race-free, unlike "SELECT, then INSERT if missing". NorthCart uses it to create each user's cart exactly once
(`ON CONFLICT (user_id) DO NOTHING`).

**13. Normalisation, and where did you deliberately denormalise?**
Normal forms remove duplication, so each fact is stored once. Deliberate exceptions:
- `products.sold_count`, for sorting by popularity without aggregating all orders;
- price and address snapshots in orders, which are history, not duplication;
- the generated `search_vector`.

Each one has a single writer that keeps it correct.

**14. Why put rules in constraints if the Java code already validates?**
Code has bugs, and there are other writers (scripts, migrations, a future service). Constraints are the last line of defence,
and they're race-proof: `CHECK (quantity_available >= 0)`, unique `(user_id, idempotency_key)`,
`CHECK (total = subtotal + shipping_fee)`. Named constraints let the API turn violations into friendly messages.

**15. Why Flyway, and the rules for migrations?**
Versioned SQL files (`V1__…`) are applied in order and recorded in `flyway_schema_history`. Every environment has the
same schema, and Hibernate only *validates* it. The rules:
- never edit an applied migration; add a new one;
- make changes backward-compatible (expand, then contract) for zero-downtime deploys;
- repeatable `R__` files are for things like seed data, and re-run when their checksum changes.

---

## Part 4: 15 React & JavaScript questions

**1. How is the React app structured?**
- `pages/` (route screens, lazy-loaded) and `components/` (`ui/`, `layout/`, `product/`, `order/`, `admin/`);
- `context/` (Auth, Cart) and `hooks/` (`useAsync`, `useDebounce`, `usePayment`, …);
- `api/` (one Axios client + `endpoints.js`) and `utils/` (formatting, errors, validation).

Pages never build URLs or parse errors themselves.

**2. Context API vs Redux: why Context here?**
Only two pieces of state are truly global: the auth user and the cart count. Context handles that with no
library. Everything else is page data, fetched where it's used. Redux, Zustand or React Query make sense once there's lots
of shared, cached server state or complex updates.

**3. How do protected routes work?**
`ProtectedRoute` reads `AuthContext`. While the stored token is being validated it shows a spinner; when logged out it redirects to login and
remembers where you came from (`state.from`). The wrong role gets redirected too, and `GuestRoute` does the opposite. It's UX only:
**the server enforces security**, and the client checks only decide what to show.

**4. How does the app handle an expired token?**
An Axios response interceptor sees a 401 on a request that *carried* a token (so a failed login doesn't count), clears the token and notifies `AuthContext`, which sends the user to login.
One central place, instead of every page handling 401s.

**5. What problem does `useAsync` solve?**
Loading, error and data state for a request, plus **stale-response protection**. If the user types "ke" then "key",
the slower "ke" response must not overwrite the "key" results. Each effect run has a local `active` flag that its
cleanup sets to `false` when the inputs change or the component unmounts. Only a still-active run may set state.
`reload()` bumps a version number to re-run it.

**6. Why keep filters in the URL?**
`/products?q=lamp&sort=price_asc&page=2` survives a refresh, the back button works, links can be shared, and the
URL is the single source of truth (`useSearchParams`). Duplicating that state in `useState` creates two copies that drift
apart.

**7. Explain `useEffect` dependencies and cleanup.**
The effect runs after render, and again whenever a dependency changes. The cleanup function runs before the next run and on
unmount: cancel timers, ignore stale requests. A bug I fixed: `useEffect(() => window.scrollTo(0, 0))` *returned*
scrollTo's value, and newer Chrome returns a Promise from it, so React tried to call a Promise as the cleanup ("destroy is
not a function"). Effect bodies should always be blocks.

**8. Why do lists need `key`, and why not the index?**
React matches old and new items by key to reuse their DOM and state. With index keys, inserting or removing an item shifts the
keys, so state such as an input's value attaches to the wrong row. Use stable ids (`productId`, `itemId`).

**9. What is debouncing, and where?**
It delays an action until input pauses (for example 300 ms), so typing "keyboard" sends one search request, not eight.
`useDebounce` is used in the admin search boxes and the price filters.

**10. Controlled vs uncontrolled inputs?**
Controlled: React state holds the value (`value` + `onChange`), so it's easy to validate and format. Uncontrolled: the DOM holds it,
and you read it with a ref. NorthCart's forms are controlled, with client-side validation that mirrors the server's rules (the server
still validates).

**11. Code splitting with `React.lazy`?**
`const AdminDashboard = lazy(() => import('./AdminDashboardPage'))` creates a separate JS chunk that loads on first
visit, inside `<Suspense>`. Customers never download the admin code, so the first load is smaller.

**12. How do you prevent XSS in React?**
JSX escapes values by default. Avoid `dangerouslySetInnerHTML`, validate URLs before using them (`javascript:` links), and set
security headers (CSP, `nosniff`). A token in `localStorage` is exactly what XSS would steal, so this matters.

**13. `==` vs `===`, and truthy/falsy gotchas in JSX?**
`===` compares without type coercion; always use it. In JSX, `{count && <Badge/>}` renders a literal **0** when count is 0.
Use `{count > 0 && …}`.

**14. Promises vs async/await; how do you handle errors?**
`async/await` is syntax over promises. `await` pauses the async function until the promise settles, without blocking the thread.
Errors go through `try/catch`. NorthCart's `utils/errors.js` turns any Axios error into one shape: the message and code from
`ApiError`, or a network or unknown fallback.

**15. How does the Vite proxy work, and why not just call `localhost:8080`?**
The dev server forwards `/api/*` to the backend, so the browser only ever talks to `localhost:5173`: same origin, so no CORS.
The production nginx config does the same, so the code uses the relative `/api/v1` everywhere.

---

## Part 5: 10 architecture & scenario questions

**1. Traffic grows 100×. What breaks first, and what do you do?**
First, the database:
- the connection pool (instances × 10);
- the catalog `count(*)`;
- dashboard aggregates.

Steps, in order:
1. Measure (`pg_stat_statements`).
2. Cache the public catalog (CDN or Redis, 30–60 s).
3. Read replica for catalog and reporting.
4. PgBouncer.
5. A daily roll-up table for the dashboard.
6. Scale the stateless API horizontally.

The search service and queues come only when data says so.

**2. A flash sale: 10,000 people, 100 units. Is the design OK?**
Correctness holds, because the conditional UPDATE never oversells, but every buyer serialises on one inventory row
lock, so latency spikes. Options:
- a short timeout and a quick 409 "sold out";
- a virtual waiting room / queue in front of checkout;
- pre-split stock into buckets (N rows of 10 units);
- reserve in Redis with Lua, then persist asynchronously (harder to keep consistent).

**3. The customer paid, but the browser crashed before `/payments/verify`. What happens?**
The order is still PLACED. Before expiring it, the expiry job asks the gateway for captured payments
(`reconcileExpiringPayments`), so it confirms the order instead of cancelling it. Production would add a Razorpay
**webhook** so confirmation doesn't depend on the browser at all.

**4. The SMTP provider is down for an hour. Effect?**
Checkout keeps working, because emails are async and after commit, but those emails are lost (logged as failures). The fix is a
**transactional outbox**: insert an `email_outbox` row in the same transaction, and a worker sends and retries with backoff.
That gives at-least-once delivery.

**5. How would you split NorthCart into microservices if you had to?**
Along the existing modules: Catalog, Inventory, Order, Payment, Notification, each with its own database. Checkout becomes a
**saga**: Order created (PENDING) → Inventory reserves (or rejects) → Payment → Order confirmed, with compensations
(release stock) on failure. Events go through Kafka or RabbitMQ via an outbox. Costs: eventual consistency, idempotent consumers,
distributed tracing.

**6. How would you add product reviews?**
- A `reviews` table (user, product, rating 1–5 CHECK, text, created_at) with a unique `(user_id, product_id)`.
- Only allow verified buyers: a DELIVERED `order_items` row must exist.
- Denormalise `rating_avg` / `rating_count` on products, updated in the same transaction.
- Paginated `GET /products/{slug}/reviews`, and admin moderation.

**7. How would you add discount coupons safely?**
- A `coupons` table (code, type percent/flat, value, min order, valid from/to, max uses, per-user limit).
- Apply at checkout inside the same transaction.
- Count usage with a conditional UPDATE (`used < max_uses`), so it can't be over-redeemed, the same pattern as stock.
- Snapshot the discount on the order.
- Never trust a client-side total.

**8. An admin says "the dashboard is slow". Walk through it.**
Reproduce it, and get the request id and timing from the logs. Run `EXPLAIN (ANALYZE, BUFFERS)` on each dashboard query with production-like
data. That's what I did: the sales query scanned all orders because the date condition wrapped the column. The fix was
filtering on the bare column first plus a partial index (146 → 24 ms). Then verify the results are identical and add a test
for the edge cases.

**9. How would you implement refresh tokens?**
- An access token of about 15 min plus a random refresh token in an `httpOnly; Secure; SameSite=Strict` cookie.
- Store a hash of the refresh token with user, expiry and family id.
- `POST /auth/refresh` rotates it: issue a new one and revoke the old one.
- If a revoked token is reused, revoke the whole family (it was stolen).
- Logout deletes the row.
- Add CSRF protection for the refresh endpoint, because it's cookie-based.

**10. How do you deploy without downtime when the schema changes?**
Expand, then contract. For example, renaming `phone` to `mobile`:
1. Add `mobile`.
2. Deploy code that writes both and reads `phone`.
3. Backfill.
4. Deploy code that reads `mobile`.
5. In a later release, drop `phone`.

Each step is compatible with the previous version, so old and new instances can run side by side during a rolling deploy. Flyway runs
each step at startup.

---

## Numbers to remember

| | |
|---|---|
| Endpoints | ~30 REST endpoints under `/api/v1` (+ health, OpenAPI) |
| Tables | 14, Flyway migrations V1–V5 |
| Tests | ~140 backend (Testcontainers PostgreSQL), 31 frontend, 28-step smoke test, 46 Postman requests |
| JWT | HS256, 2 h, stateless, user re-loaded per request |
| BCrypt | strength 12 · reset tokens SHA-256-hashed, 15 min, single use |
| Shipping | ₹49, free from ₹999 · unpaid orders expire after 30 min |
| Perf dataset | 50k products · 200k orders · 400k order items |
| Perf wins | search 18.6 → 3.4 ms · sales chart 146 → 24 ms · best sellers 397 → 117 ms · summary 121 → 79 ms |
