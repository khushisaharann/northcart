# Troubleshooting

Start with the **request id**. Every response carries `X-Request-Id`, and so does every error body
(`"requestId": "4f9c2a1b"`). Search the backend console for it to see every log line of that request:

```
INFO  [4f9c2a1b] c.n.order.service.OrderService : Order NC-20261106-7F3K9A placed ...
```

## Starting up

| Symptom | Cause → fix |
|---|---|
| `docker compose up` → `port is already allocated` / `address already in use :5432` | A local PostgreSQL (often Homebrew's) already uses 5432. Either `brew services stop postgresql@16`, or set `POSTGRES_PORT=5433` **and** `DB_URL=jdbc:postgresql://localhost:5433/northcart` in `.env`. |
| `Cannot connect to the Docker daemon` | Docker Desktop isn't running. Start it and wait until the whale icon stops animating. |
| Backend: `Connection to localhost:5432 refused` | The DB container isn't up yet: `docker compose ps` should show `healthy`. |
| Backend: `password authentication failed for user "northcart"` | The volume was created with other credentials. Reset it with `docker compose down -v && docker compose up -d` (**deletes local data**). |
| Backend: `FlywayValidateException: Validate failed: Migrations have failed validation` / checksum mismatch | A migration file that was already applied has been edited. Never edit an applied migration; add a new `V6__...`. Locally you can reset with `docker compose down -v`. |
| Backend: `Schema-validation: missing column [...]` | The entity and the migrations disagree (`ddl-auto=validate` catches this on purpose). Add a migration. |
| `release version 21 not supported` / `invalid target release: 21` | Maven runs on an older JDK. `mvn -v` must say Java 21. On a Mac: `export JAVA_HOME=$(/usr/libexec/java_home -v 21)`. |
| Lombok errors: `cannot find symbol: method getId()` in the IDE only | Enable annotation processing (IntelliJ: Settings → Build → Compiler → Annotation Processors). VS Code: install "Extension Pack for Java", then *Java: Clean Java Language Server Workspace*. |
| `Port 8080 was already in use` | `lsof -i :8080` and stop the other process, or set `SERVER_PORT=8081` (and `VITE_API_PROXY_TARGET=http://localhost:8081` for the frontend). |
| `/actuator/health` says `DOWN` with `mail` DOWN | Mailpit isn't running: `docker compose up -d mailpit`. |

## Docker stack (`--profile app`)

| Symptom | Cause → fix |
|---|---|
| Backend container restarts; log says `app.jwt.secret must be a Base64-encoded key of at least 256 bits` (or `Could not resolve placeholder 'JWT_SECRET'`) | The prod profile has no dev key. `echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env`, then `docker compose --profile app up -d`. |
| `http://localhost:3000` shows **502 Bad Gateway** for API calls | The backend is still starting (about 20–40 s) or crashed. Check `docker compose ps` and `docker compose logs backend`. |
| Admin login fails with the `ADMIN_PASSWORD` from `.env` | The account already existed (e.g. created by the dev backend), and the bootstrap never overwrites it. Use the old password (`Admin12345` in dev), or pick a new `ADMIN_EMAIL`. |
| No products in the Docker stack | `FLYWAY_LOCATIONS` was set to `classpath:db/migration` only, so the sample catalog wasn't loaded. That's correct for production. |
| `port is already allocated` on 3000 | Set `APP_PORT=3001` in `.env`. |
| Build fails at `mvn ... dependency:go-offline` / `npm ci` | No internet from Docker, or a corporate proxy. Check that `docker run --rm alpine wget -qO- https://repo.maven.apache.org` works. |
| Changes not showing after `up -d` | Add `--build`. Without it Compose reuses the old image. |

## Tests

| Symptom | Cause → fix |
|---|---|
| `Could not find a valid Docker environment` | Docker Desktop isn't running. Integration tests start a real PostgreSQL in a container. |
| `client version 1.32 is too old. Minimum supported API version is 1.44` | Docker Engine 29+ with Testcontainers 1.x. `src/test/resources/docker-java.properties` (`api.version=1.44`) fixes it. Check the file is there. |
| First `mvn test` is slow | It downloads the `postgres:16-alpine` image once. Later runs reuse it, and one container is shared by all test classes. |
| A test fails only when run with all the others | Tests share one database. Use unique emails and names (`uniqueEmail()`, `unique()`), never fixed ones. |
| `npm test` → `window.scrollTo is not a function` / `destroy is not a function` | An effect returned a value. `useEffect` bodies must be blocks: `useEffect(() => { window.scrollTo(0, 0); }, [])`. |

## Frontend ↔ backend

| Symptom | Cause → fix |
|---|---|
| Every API call fails with `ERR_CONNECTION_REFUSED` / 504 from Vite | Backend not running, or on another port (`VITE_API_PROXY_TARGET`). |
| Browser console: `blocked by CORS policy` | You're calling `http://localhost:8080` directly instead of the relative `/api` (which goes through the Vite proxy), or your origin isn't in `CORS_ALLOWED_ORIGINS`. |
| Logged out right after logging in | The token was rejected (401). Usually the backend restarted with a **different `JWT_SECRET`**, so old tokens are invalid. Log in again. |
| Admin pages redirect to `/admin/login` | The logged-in account is a customer. Use the admin account (`admin@northcart.local` / `Admin12345` in dev). |
| Changes to `.env` ignored by the frontend | Vite reads `frontend/.env` at startup, and only `VITE_*` variables. Restart `npm run dev`. |

## Business behaviour that looks like a bug

| What you see | Why |
|---|---|
| An unpaid order disappeared / became CANCELLED | Unpaid orders expire after 30 min (`app.orders.payment-timeout`) and the stock is released. |
| Stock dropped when an order was *placed*, before payment | On purpose: stock is reserved at checkout, so two people can't pay for the last unit. |
| Order detail says ₹1,250 but the product now costs ₹1,400 | Orders keep the price at the time of purchase (snapshot). |
| Password reset email never arrives | It's in **Mailpit** (http://localhost:8025), not your real inbox. Also: "forgot password" answers 200 even for unknown emails, by design. |
| Search for "keyb" finds keyboards, "eyboard" finds nothing | Full-text search matches word **prefixes**, not substrings. |
| `/payments/fake/pay` returns 404 | It exists only with `PAYMENT_PROVIDER=fake`. With Razorpay the real popup is used. |

## Inspecting the database

```bash
docker exec -it northcart-postgres psql -U northcart -d northcart

\dt                                    -- tables
SELECT * FROM flyway_schema_history;   -- applied migrations
SELECT order_number, status, total, created_at FROM orders ORDER BY id DESC LIMIT 5;
SELECT p.name, i.quantity_available FROM inventory i JOIN products p ON p.id = i.product_id;
```

Turn on SQL logging temporarily in `application-dev.yml`:

```yaml
logging.level:
  org.hibernate.SQL: debug                               # every statement
  org.hibernate.orm.jdbc.bind: trace                     # with parameter values
```
