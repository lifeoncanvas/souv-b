# LICHT Signature Souvenirs — API

Spring Boot 3.5 (Java 21) REST API with Spring Security + JWT, Spring Data JPA and Flyway, on
[Neon](https://neon.tech) serverless PostgreSQL. The storefront (separate repository, [lifeoncanvas/sov-frontend](https://github.com/lifeoncanvas/sov-frontend))
calls it at `/api`.

## Run locally

```bash
./mvnw spring-boot:run
```

On Windows: `.\mvnw.cmd spring-boot:run`. The API listens on http://localhost:8090.

**No setup needed for development:** without `DATABASE_URL` the backend starts on an embedded local database
(`data/`, git-ignored) seeded with the sample catalogue, and prints a warning saying so.

**To use the shared Neon database**, copy `.env.example` to `.env` (git-ignored) and set:

- `DATABASE_URL`: Neon connection string (Neon console → project → **Connect**; pooled endpoint recommended).
- `JWT_SECRET`: a new random value of at least 32 characters (e.g. `openssl rand -base64 48`).
- `ADMIN_EMAIL` / `ADMIN_PASSWORD`: the first administrator, created on an empty database. Without a password,
  a generated one is printed once in the log.

Ask the project owner for the real values privately; never commit `.env`.

The schema is created by the Flyway migrations in `src/main/resources/db/migration`; placeholder content is
seeded into empty tables. Settings live in `src/main/resources/application.properties` (local-mode overrides in
`application-local.properties`); every value can be overridden by an environment variable or `.env`.

API docs (local mode, or `API_DOCS_ENABLED=true`): http://localhost:8090/api/docs

Tests (in-memory H2 built from the same migrations):

```bash
./mvnw test
```

## Open in IntelliJ IDEA

1. **File → Open** and choose **this repository's folder** (the one containing `pom.xml`), not a parent folder.
   IntelliJ imports it as a Maven project.
2. **File → Project Structure → Project → SDK**: choose a **JDK 21 or newer** (Download JDK… if none is listed).
3. Optional: copy `.env.example` to `.env` and fill in the Neon values to use the shared database. Without it, the
   app runs on a local sample database.
4. Run the shared run configuration **LichtApplication** (top-right dropdown). It runs from this folder, so `.env`
   is found. Wait for `Started LichtApplication`, then the API is at http://localhost:8090.

**Troubleshooting**
- `Port 8090 was already in use`: another copy of the backend is already running (another IntelliJ window or a
  terminal). Stop it, or set `PORT=8091` in `.env`. Only one copy can run at a time.
- `No database configured` / `.env` ignored: the run configuration's working directory isn't this folder. Use the
  shared **LichtApplication** configuration, or set *Working directory* to `$PROJECT_DIR$`.
- `release version 21 not supported` / `invalid target release`: the project SDK is older than Java 21 (step 2).
- The storefront shows `ECONNREFUSED` in its terminal: the backend isn't running yet. Start it first.

## Deploy on a Linux server

The API runs as a service behind nginx at `https://api.lichtsignaturesouvenirs.store`. The storefront (separate repo)
is built with that address. Files are in `deploy/`.

1. Point a DNS **A record** for `api.lichtsignaturesouvenirs.store` at the server.
2. Install Java 21, nginx and certbot. On Ubuntu:
   `sudo apt install openjdk-21-jre-headless nginx certbot python3-certbot-nginx`
3. Build the jar (on the server or anywhere with JDK 21): `./mvnw -DskipTests package`, which produces
   `target/licht-souvenirs-api-1.0.0.jar`.
4. Install the service, following the steps at the top of `deploy/licht-api.service`. Fill in `/etc/licht/api.env`
   from `deploy/api.env.example`: the database URL, a new `JWT_SECRET`, the admin password, SMTP.
5. Install the nginx site and HTTPS, following the steps at the top of `deploy/nginx-api.conf`. Always run
   `sudo nginx -t` before reloading.
6. Check: `curl https://api.lichtsignaturesouvenirs.store/api/categories` returns JSON.
   Logs: `journalctl -u licht-api -f`.

**To update:** pull, rebuild the jar, copy it to `/opt/licht/api.jar`, then `sudo systemctl restart licht-api`.

The service runs with `SPRING_PROFILES_ACTIVE=prod`. In that mode the API **refuses to start** without
`DATABASE_URL`, rather than quietly using an empty local database.

## Production configuration (environment variables)

**Always set `DATABASE_URL` and `SPRING_PROFILES_ACTIVE=prod` in production.**

| Variable | Purpose |
| --- | --- |
| `DATABASE_URL` | Neon connection string (`postgresql://…` or `jdbc:postgresql://…`) — **required** |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | Optional; override credentials embedded in the URL |
| `DB_POOL_SIZE` | Connection pool size (default 3) |
| `JWT_SECRET` | Long random secret (≥ 32 bytes) — **required** |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | First administrator |
| `PORT` | HTTP port (default 8090) |
| `CORS_ALLOWED_ORIGINS`, `FRONTEND_URL` | Storefront domain |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD`, `MAIL_FROM`, `ADMIN_NOTIFY_EMAIL` | Transactional email. When unset, emails are logged without their bodies |
| `UPLOAD_DIR`, `UPLOAD_PUBLIC_BASE_URL`, `ORPHAN_LOGO_HOURS` | Storage for customer logo uploads |
| `STORE_CURRENCY` | ISO currency (default `GBP`) |
| `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET`, `PAYPAL_MODE`, `PAYPAL_WEBHOOK_ID`, `PAYPAL_BRAND_NAME` | PayPal checkout; hidden until id and secret are set |
| `TURNSTILE_SECRET_KEY` | Optional Cloudflare Turnstile CAPTCHA (frontend needs `VITE_TURNSTILE_SITE_KEY`) |
| `UNPAID_ORDER_EXPIRY_HOURS`, `PAYPAL_ORDER_EXPIRY_MINUTES` | Unpaid orders are cancelled and restocked after 168 h / 60 min |
| `MAX_QUANTITY_PER_PRODUCT`, `GUEST_CART_DAYS` | Per-order quantity cap (100); guest bag lifetime (30 days) |
| `FORWARD_HEADERS_STRATEGY` | `native` (default) behind a private-network proxy; `none` when exposed directly |
| `API_DOCS_ENABLED` | Swagger UI at `/api/docs` |
| `JPA_DDL_AUTO` | `validate` (default); add schema changes as new `V…__….sql` migrations |
| `SHIPPING_STANDARD_FEE`, `SHIPPING_EXPRESS_FEE`, `SHIPPING_FREE_THRESHOLD` | Delivery pricing |

## PayPal checkout

1. In the [PayPal Developer dashboard](https://developer.paypal.com/dashboard/applications), create a REST app
   (start in Sandbox). Set `PAYPAL_CLIENT_ID`, `PAYPAL_CLIENT_SECRET` and `PAYPAL_MODE=sandbox`.
2. Add a webhook pointing at `https://<your-api>/api/payments/paypal/webhook` with the events
   `CHECKOUT.ORDER.APPROVED`, `PAYMENT.CAPTURE.COMPLETED`, `PAYMENT.CAPTURE.PENDING`, `PAYMENT.CAPTURE.DENIED`,
   `PAYMENT.CAPTURE.REFUNDED` and `PAYMENT.CAPTURE.REVERSED`. Put its id in `PAYPAL_WEBHOOK_ID`.
3. Restart; PayPal appears at checkout. Switch to live credentials and `PAYPAL_MODE=live` when ready.

## Launch checklist

- `DATABASE_URL` and a fresh `JWT_SECRET` set; `ADMIN_PASSWORD` set and changed after first sign-in.
- SMTP configured so password resets and order emails are delivered.
- `CORS_ALLOWED_ORIGINS` / `FRONTEND_URL` set to the live storefront domain; HTTPS in front of the API.
- `FORWARD_HEADERS_STRATEGY` matches your hosting (rate limits rely on the real client IP).
- Run a single instance, or move the in-memory rate limiter to a shared store (e.g. Redis).
