# Deployment

NorthCart ships as **two container images** plus a PostgreSQL database:

| Image | Built from | Contains | Size driver |
|---|---|---|---|
| `northcart-backend` | `backend/Dockerfile` | JRE 21 + the layered Spring Boot app, non-root, health check | JRE (~190 MB) |
| `northcart-frontend` | `frontend/Dockerfile` | nginx serving the built React files and proxying `/api` | nginx (~50 MB) |

```
                 ┌─────────────────────────── one origin: https://shop.example.com ───┐
 browser ──HTTPS─▶ TLS proxy ──▶ frontend (nginx) ── /           → React static files │
                                                  └─ /api/...   → backend:8080 ─▶ PostgreSQL
                 └────────────────────────────────────────────────────────────────────┘
```

Serving the app and the API from **one origin** is deliberate: the browser makes no cross-origin
calls, so there's no CORS preflight and no CORS misconfiguration to get wrong.

## 1. Run the production images locally

```bash
# once: a real signing key and an admin account in .env
echo "JWT_SECRET=$(openssl rand -base64 32)" >> .env
echo "ADMIN_EMAIL=admin@northcart.local"     >> .env
echo "ADMIN_PASSWORD=ChangeMe-123"           >> .env

docker compose --profile app up -d --build     # first build: a few minutes (Maven + npm downloads)
docker compose ps                              # backend + frontend become "healthy"
open http://localhost:3000                     # Windows: start http://localhost:3000
```

- The stack runs with the **`prod` Spring profile**: no dev defaults, Swagger off, no health details.
- Stop the dev backend first (`mvn spring-boot:run`): both use the same database. For the same reason, if the dev
  backend already created `admin@northcart.local`, it keeps its password `Admin12345`. `ADMIN_*` only creates a
  **missing** account and never overwrites one.
- The backend port is not published: everything goes through nginx on :3000.
  The smoke test needs the backend directly, so uncomment `ports: 8080:8080` in `docker-compose.yml`, then run
  `node scripts/smoke-test.mjs` (with `ADMIN_EMAIL` / `ADMIN_PASSWORD` set to your values).
- Logs: `docker compose logs -f backend`. Every line has the `[requestId]`.

## 2. Deploy to a server

### Option A: one Linux VM with Docker (simplest, the whole stack)

Any VM with 2 GB RAM is enough.

1. Install Docker and point a domain's DNS (A record) at the VM.
2. Copy the repo (or pull pre-built images from a registry) and create `.env` with **real** values:
   strong `POSTGRES_PASSWORD`, `JWT_SECRET`, `ADMIN_PASSWORD`, `FLYWAY_LOCATIONS=classpath:db/migration`,
   `APP_PORT=8081`, plus SMTP and Razorpay settings (below).
3. Put a TLS-terminating proxy in front. Caddy gets and renews Let's Encrypt certificates automatically:
   ```
   # /etc/caddy/Caddyfile
   shop.example.com {
       reverse_proxy localhost:8081
   }
   ```
4. In `docker-compose.yml`:
   - set `FRONTEND_URL` and `CORS_ALLOWED_ORIGINS` to `https://shop.example.com`;
   - remove the `ports:` of `postgres` (the DB must not be reachable from the internet);
   - remove `mailpit` and set `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` to a real SMTP provider.
5. `docker compose --profile app up -d --build`

### Option B: managed services (no server to maintain)

| Piece | Where | Settings |
|---|---|---|
| Database | Any managed PostgreSQL 16 | Copy the JDBC URL into `DB_URL` (add `?sslmode=require` if the provider needs it) |
| Backend | Any container platform (Render, Railway, Fly.io, AWS ECS/App Runner, Google Cloud Run…) | Image from `backend/Dockerfile`; port 8080; health check path `/actuator/health`; all env vars from the checklist |
| Frontend | Same platform as a second container (keeps one origin), **or** a static host | Static host: build with `VITE_API_BASE_URL=https://api.example.com/api/v1`, add an SPA rewrite (all paths → `/index.html`), and set `CORS_ALLOWED_ORIGINS` on the backend to the frontend's exact URL |

Cloud Run–style platforms scale to zero, and the first request after idle then waits for the JVM
to start (several seconds). That is fine for a portfolio. For a real shop, keep one instance warm.

## 3. Production checklist

**Secrets and config.** Set these in the platform's secret store, never in git:

| Variable | Notes |
|---|---|
| `JWT_SECRET` | `openssl rand -base64 32`. Changing it logs everyone out. |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | A dedicated DB user, not the superuser |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | Strong password. You can remove both after the first start; the account stays. |
| `PAYMENT_PROVIDER=razorpay`, `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | The prod profile has **no** default provider, so it is an explicit choice. Use live keys only after testing with test keys. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | Real SMTP (transactional email provider). A verified sender domain avoids spam folders. |
| `FRONTEND_URL`, `CORS_ALLOWED_ORIGINS` | The public `https://` URL, exactly |
| `FLYWAY_LOCATIONS=classpath:db/migration` | Docker only: **don't** load the sample catalog |

**Before going live**

- [ ] HTTPS everywhere. The JWT travels in a header, so plain HTTP leaks it.
- [ ] The database is not exposed publicly. Automatic daily backups are on, and **a restore was tested once**.
- [ ] `docker compose logs backend` shows Flyway "Successfully applied" or "Schema is up to date".
- [ ] The smoke test passes against the deployment (`BASE_URL=https://… node scripts/smoke-test.mjs`), using a test admin.
- [ ] Razorpay webhooks are not used. Payment is confirmed by `/payments/verify` plus the expiry job's reconciliation. Make sure the server clock is NTP-synced.
- [ ] Memory: the JVM uses 75 % of the container limit (`MaxRAMPercentage`). Give the backend 512 MB–1 GB.

## 4. Operating it

| Task | How |
|---|---|
| Deploy a new version | Build, then restart the backend. Flyway applies new migrations **at startup**, before traffic. With several instances, Flyway's lock table makes one migrate while the others wait. |
| Zero-downtime-friendly migrations | Expand, then contract: add the new column → deploy code that writes both → backfill → deploy code that reads the new one → drop the old one in a later release. Never rename in one step. |
| Graceful shutdown | Spring Boot finishes in-flight requests on SIGTERM. The image runs `java` as PID 1 (exec form), so it receives the signal. |
| Health | `/actuator/health` (database + mail). The platform restarts the container after repeated failures. |
| Debug a user's error | Ask for the `requestId` from the error toast/response, then search the logs for it. |
| Back up | `docker exec northcart-postgres pg_dump -U northcart -Fc northcart > northcart-$(date +%F).dump` |
| Restore | `pg_restore -U northcart -d northcart --clean northcart-YYYY-MM-DD.dump` |
| Scale out | The API is stateless (JWT, no HTTP session), so add instances behind the load balancer. The expiry job is safe on many instances (`FOR UPDATE SKIP LOCKED`). Watch the total DB connections: instances × 10 (Hikari pool). |

## 5. CI

`.github/workflows/ci.yml` runs on every push and PR:

1. **backend**: `mvn verify`, all unit and Testcontainers integration tests. GitHub runners have Docker.
2. **frontend**: `npm ci`, `npm test`, `npm run build`.
3. **docker-images**: builds both images, only when 1 and 2 are green.

The natural next step is to push the images to a registry (GHCR) on `main`, and let the platform deploy the new tag.
