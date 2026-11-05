# LICHT Signature Souvenirs — API

Spring Boot 3.5 (Java 21) REST API with Spring Security + JWT, Spring Data JPA and Flyway, on
[Neon](https://neon.tech) serverless PostgreSQL. The storefront (separate repository, `souvenirs-frontend`)
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

## Production configuration (environment variables)

**Always set `DATABASE_URL` in production**; otherwise the API runs on an empty local database.

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
