# Deployment Guide

This is the authoritative setup and deployment guide for the repository. It is based on the current env examples, Docker Compose file, Render Blueprint, Spring configuration, Vite configuration, and Stripe integration.

## Local Development

### Prerequisites

- Java 21
- Node.js 20+ and npm
- Docker Desktop, or another PostgreSQL 16-compatible database
- Stripe test keys for full payment testing

### Environment Files

Copy the local examples:

```bash
cp .env.example .env
cp backend/.env.local.example backend/.env.local
cp frontend/.env.development.example frontend/.env.development
```

The copied files are templates. Edit them before starting the backend; angle-bracket values such as `<local-postgres-password>` and `<replace-with-a-long-local-dev-secret>` are not ready-to-run secrets.

Set `.env`:

```env
POSTGRES_PASSWORD=<local-postgres-password>
```

`docker-compose.yml` passes `POSTGRES_PASSWORD` into the local PostgreSQL container. Keep this value aligned with `SPRING_DATASOURCE_PASSWORD` in `backend/.env.local`.

Minimum local backend values:

```env
SPRING_PROFILES_ACTIVE=dev
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/booking_engine
SPRING_DATASOURCE_USERNAME=postgres
SPRING_DATASOURCE_PASSWORD=<same-local-postgres-password>
APP_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
APP_HAIR_SALON_ID=550e8400-e29b-41d4-a716-446655440000
APP_BOOKING_TIMEZONE=Europe/Dublin
APP_JWT_SECRET=<generate-a-strong-random-secret>
APP_STRIPE_SECRET_KEY=<stripe-test-secret-key>
APP_STRIPE_WEBHOOK_SECRET=<stripe-test-webhook-secret>
APP_STRIPE_PUBLISHABLE_KEY=<stripe-test-publishable-key>
APP_STRIPE_CURRENCY=eur
```

The backend validates JWT and Stripe configuration at startup. `APP_JWT_SECRET` must be a strong, non-placeholder secret; empty, weak, placeholder, or undersized values fail startup. Stripe values must be configured, and valid matching Stripe test values are required for checkout/payment testing.

Minimum local frontend values:

```env
VITE_DEV_API_PROXY_TARGET=http://localhost:8080
VITE_API_BASE_URL=
```

When `VITE_API_BASE_URL` is empty, frontend requests use the current browser origin and Vite proxies `/api` to `VITE_DEV_API_PROXY_TARGET`.

### Start Local Services

Start PostgreSQL:

```bash
docker compose up -d postgres
```

Run the backend:

```bash
cd backend
mvn spring-boot:run
```

Run the frontend:

```bash
cd frontend
npm ci
npm run dev
```

Local URLs:

- Public frontend: `http://localhost:5173`
- Admin entry: `http://localhost:5173/admin.html`
- Backend API: `http://localhost:8080`
- Backend health: `http://localhost:8080/actuator/health`

Flyway runs migrations from `backend/src/main/resources/db/migration` when the backend starts.

A fresh local database contains the base salon configuration only. Treatments, employees, and schedules can be created through the admin interface before testing the full booking flow.

### Local Admin Bootstrap

Flyway does not seed an admin user. To create the first local admin account, temporarily add these backend env values:

```env
APP_ADMIN_BOOTSTRAP_ENABLED=true
APP_ADMIN_BOOTSTRAP_USERNAME=<local-admin-username>
APP_ADMIN_BOOTSTRAP_PASSWORD=<strong-local-admin-password>
APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE=false
```

Start the backend once, confirm the admin can log in, then set:

```env
APP_ADMIN_BOOTSTRAP_ENABLED=false
```

For password rotation of an existing admin, see [Security Secret Rotation](security-secret-rotation.md).

## Local Stripe Testing

Basic local UI/API screens can run on localhost once backend configuration is complete. Stripe checkout testing requires values from the same Stripe test account:

- `APP_STRIPE_SECRET_KEY` comes from the Stripe Dashboard test/developer secret key.
- `APP_STRIPE_PUBLISHABLE_KEY` comes from the corresponding Stripe publishable key.
- `APP_STRIPE_WEBHOOK_SECRET` comes from Stripe CLI for local webhook forwarding, or from the webhook endpoint signing secret in the Stripe Dashboard for hosted deployments.
- The webhook endpoint is `POST /webhook`.

For local webhook forwarding with Stripe CLI:

- Forward events to `http://localhost:8080/webhook`.
- Set `APP_STRIPE_WEBHOOK_SECRET` to the signing secret printed by that `stripe listen` session.
- The backend verifies all webhook requests with `APP_STRIPE_WEBHOOK_SECRET`.

With Stripe CLI, forward events to the backend conceptually like this:

```bash
stripe listen --forward-to http://localhost:8080/webhook
```

Do not commit Stripe keys or webhook secrets.

### Wallet and HTTPS Notes

The frontend uses Stripe Payment Element and Express Checkout for wallet-style methods. Browser wallet/payment methods such as Google Pay generally require a secure HTTPS context and will not behave the same on plain `http://localhost` or ordinary Vite dev URLs.

Wallet availability may also depend on browser, device, Stripe account configuration, currency, region, and payment method availability. Do not assume Google Pay or Apple Pay will appear in every local environment.

For HTTPS local testing, use a tunnel such as ngrok or another HTTPS-capable local tunnel.

If the HTTPS tunnel points to the frontend dev server:

- Set `VITE_DEV_ALLOWED_HOSTS` to the tunnel host name, for example `your-ngrok-domain.ngrok-free.app`.
- Add the full tunnel origin to `APP_CORS_ALLOWED_ORIGINS`, for example `https://your-ngrok-domain.ngrok-free.app`.
- If the frontend still proxies API requests to the local backend, keep `VITE_DEV_API_PROXY_TARGET=http://localhost:8080`.

If the HTTPS tunnel points to the backend instead, set `VITE_API_BASE_URL` to that backend tunnel origin and include the frontend origin in `APP_CORS_ALLOWED_ORIGINS`.

## Render Deployment

`render.yaml` defines:

- Backend web service: Docker-based Spring Boot service from `backend/Dockerfile`
- Frontend web service: Render Static Site from `frontend/dist`
- Backend health check: `/actuator/health`

The repository expects an external PostgreSQL database. The JDBC guidance below is compatible with Neon-style hosted PostgreSQL, but CI does not provision or test Neon.

### URL Bootstrap Sequence

Render service URLs may not be known before the first deploy, while the backend and frontend both need URL-dependent configuration:

- Backend needs `APP_CORS_ALLOWED_ORIGINS`.
- Frontend needs `VITE_API_BASE_URL`.
- Frontend CSP in `render.yaml` may need tightening if the API moves away from Render/onrender.com.

Use this bootstrap sequence:

1. Create the PostgreSQL database.
2. Create the Render backend and frontend services from `render.yaml`.
3. For required URL-dependent variables that are not known yet, use temporary exact HTTPS placeholder origins, not `<placeholder>` values. The production validator rejects obvious placeholder syntax.
4. Deploy once to obtain the generated Render backend and frontend URLs.
5. Update `VITE_API_BASE_URL` to the backend service URL.
6. Update `APP_CORS_ALLOWED_ORIGINS` to the frontend static site URL.
7. If using custom domains, update both values again to the final custom domains.
8. Redeploy so CORS, frontend API routing, cookies, and URL-dependent behavior are aligned.
9. Confirm `GET /actuator/health` on the backend URL.
10. Verify public booking, Stripe test payment, admin login, and unsafe admin actions.

### Render Backend Variables

Required for production-like Render deployments:

```env
SPRING_PROFILES_ACTIVE=prod
SPRING_DATASOURCE_URL=jdbc:postgresql://...
SPRING_DATASOURCE_USERNAME=...
SPRING_DATASOURCE_PASSWORD=...
SPRING_JPA_SHOW_SQL=false
APP_CORS_ALLOWED_ORIGINS=https://your-frontend.onrender.com
APP_HAIR_SALON_ID=550e8400-e29b-41d4-a716-446655440000
APP_BOOKING_TIMEZONE=Europe/Dublin
APP_STRIPE_SECRET_KEY=...
APP_STRIPE_WEBHOOK_SECRET=...
APP_STRIPE_PUBLISHABLE_KEY=...
APP_STRIPE_CURRENCY=eur
APP_JWT_SECRET=...
APP_JWT_EXPIRATION_SECONDS=3600
APP_JWT_ISSUER=salon-booking-platform
APP_AUTH_COOKIE_SECURE=true
APP_AUTH_COOKIE_SAME_SITE=None
APP_ADMIN_BOOTSTRAP_ENABLED=false
APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE=false
```

Render also provides hosting-specific environment variables. Because `ConfigurationSafetyValidator` treats Render as production-like hosting, `SPRING_PROFILES_ACTIVE=prod` must be active.

Optional or context-specific backend variables include:

- `PORT`, usually supplied by Render or defaulting to `8080`
- `SERVER_FORWARD_HEADERS_STRATEGY`, defaulting to `native` in the prod profile
- `APP_SECURITY_TRUSTED_PROXY_CIDRS`, only for trusted reverse proxies/load balancers
- `APP_ADMIN_BOOTSTRAP_USERNAME` and `APP_ADMIN_BOOTSTRAP_PASSWORD`, only during a controlled bootstrap window
- `NVD_API_KEY`, only for dependency scan jobs

### Render Frontend Variables

Required:

```env
VITE_API_BASE_URL=https://your-backend.onrender.com
```

Optional:

```env
VITE_PUBLIC_SITE_URL=https://your-frontend.onrender.com
```

`VITE_DEV_API_PROXY_TARGET` and `VITE_DEV_ALLOWED_HOSTS` are local-development variables and are not needed for Render static-site builds.

### Admin Bootstrap in Production

For a first admin account in a new production database:

1. Set `APP_ADMIN_BOOTSTRAP_ENABLED=true`.
2. Set `APP_ADMIN_BOOTSTRAP_USERNAME`.
3. Set a strong `APP_ADMIN_BOOTSTRAP_PASSWORD`.
4. Deploy once and confirm admin login.
5. Set `APP_ADMIN_BOOTSTRAP_ENABLED=false`.
6. Remove bootstrap username/password from the platform if possible.
7. Redeploy.

For controlled password rotation, use `APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE=true` only during the rotation window. See [Security Secret Rotation](security-secret-rotation.md).

## PostgreSQL and Neon-Compatible JDBC

For Neon-compatible PostgreSQL providers, connection strings often look like:

```text
postgresql://USER:PASSWORD@HOST/DATABASE?sslmode=require
```

For Spring JDBC, split that into:

```env
SPRING_DATASOURCE_URL=jdbc:postgresql://HOST/DATABASE?sslmode=require&channelBinding=require
SPRING_DATASOURCE_USERNAME=USER
SPRING_DATASOURCE_PASSWORD=PASSWORD
```

This is deployment guidance for PostgreSQL/JDBC compatibility. The CI workflow does not create or test a Neon database.

## Environment Variables

### Database

| Variable | Required | Scope | Purpose |
| -------- | -------- | ----- | ------- |
| `POSTGRES_PASSWORD` | Yes for Docker Compose | Local | Password injected into the local PostgreSQL container. |
| `SPRING_DATASOURCE_URL` | Yes | Backend | PostgreSQL JDBC URL. |
| `SPRING_DATASOURCE_USERNAME` | Yes | Backend | PostgreSQL user. |
| `SPRING_DATASOURCE_PASSWORD` | Yes | Backend | PostgreSQL password. |
| `SPRING_JPA_SHOW_SQL` | Optional | Backend | Enables Hibernate SQL logging. Keep false outside local debugging. |

### Frontend

| Variable | Required | Scope | Purpose |
| -------- | -------- | ----- | ------- |
| `VITE_API_BASE_URL` | Yes in production | Frontend | Backend API origin for built frontend assets. Leave empty locally when using Vite proxy. |
| `VITE_DEV_API_PROXY_TARGET` | Local only | Frontend | Local backend proxy target, usually `http://localhost:8080`. |
| `VITE_DEV_ALLOWED_HOSTS` | Optional local | Frontend | Comma-separated Vite dev host allowlist, useful for ngrok/custom dev hosts. Use host names, not full URLs. |
| `VITE_PUBLIC_SITE_URL` | Optional | Frontend | Canonical public site URL for SEO metadata. |

### CORS and Allowed Hosts

| Variable | Required | Scope | Purpose |
| -------- | -------- | ----- | ------- |
| `APP_CORS_ALLOWED_ORIGINS` | Yes | Backend | Exact browser origins allowed to call the credentialed API. Wildcards are rejected. |
| `APP_SECURITY_TRUSTED_PROXY_CIDRS` | Optional | Backend | Trusted proxy IPs/CIDRs for forwarded client IP handling. |
| `SERVER_FORWARD_HEADERS_STRATEGY` | Optional prod | Backend | Spring forward-header strategy. Prod profile defaults it to `native`. |

### Authentication and Cookies

| Variable | Required | Scope | Purpose |
| -------- | -------- | ----- | ------- |
| `APP_JWT_SECRET` | Yes | Backend | JWT signing secret. Must be strong and non-placeholder. |
| `APP_JWT_EXPIRATION_SECONDS` | Optional | Backend | Admin JWT lifetime. Minimum is 300 seconds. |
| `APP_JWT_ISSUER` | Optional | Backend | JWT issuer value. |
| `APP_AUTH_MAX_FAILED_ATTEMPTS` | Optional | Backend | Failed login attempts before lockout. |
| `APP_AUTH_LOCK_DURATION_SECONDS` | Optional | Backend | Account lockout duration. |
| `APP_AUTH_RATE_LIMIT_IP_MAX_ATTEMPTS` | Optional | Backend | Login rate limit by IP. |
| `APP_AUTH_RATE_LIMIT_IP_WINDOW_SECONDS` | Optional | Backend | Login IP rate limit window. |
| `APP_AUTH_RATE_LIMIT_USERNAME_IP_MAX_ATTEMPTS` | Optional | Backend | Login rate limit by username and IP. |
| `APP_AUTH_RATE_LIMIT_USERNAME_IP_WINDOW_SECONDS` | Optional | Backend | Username/IP rate limit window. |
| `APP_AUTH_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS` | Optional | Backend | Login limiter cleanup interval. |
| `APP_AUTH_PASSWORD_POLICY_MIN_LENGTH` | Optional | Backend | Admin password minimum length. |
| `APP_AUTH_PASSWORD_POLICY_REQUIRE_UPPERCASE` | Optional | Backend | Require uppercase letters. |
| `APP_AUTH_PASSWORD_POLICY_REQUIRE_LOWERCASE` | Optional | Backend | Require lowercase letters. |
| `APP_AUTH_PASSWORD_POLICY_REQUIRE_DIGIT` | Optional | Backend | Require digits. |
| `APP_AUTH_PASSWORD_POLICY_REQUIRE_SPECIAL_CHARACTER` | Optional | Backend | Require special characters. |
| `APP_AUTH_PASSWORD_POLICY_REJECTED_VALUES` | Optional | Backend | Comma-separated rejected weak password values. |
| `APP_AUTH_COOKIE_NAME` | Optional | Backend | Admin auth cookie name. |
| `APP_AUTH_COOKIE_PATH` | Optional | Backend | Admin auth cookie path. |
| `APP_AUTH_COOKIE_SECURE` | Yes in prod | Backend | Must be true for production HTTPS and SameSite=None. |
| `APP_AUTH_COOKIE_SAME_SITE` | Yes in prod | Backend | `Strict`, `Lax`, or `None`. `None` requires secure cookies. |

### Stripe

| Variable | Required | Scope | Purpose |
| -------- | -------- | ----- | ------- |
| `APP_STRIPE_SECRET_KEY` | Yes | Backend | Stripe server secret key. |
| `APP_STRIPE_WEBHOOK_SECRET` | Yes | Backend | Stripe webhook signing secret. |
| `APP_STRIPE_PUBLISHABLE_KEY` | Yes | Backend/frontend config API | Publishable key returned to the browser checkout config endpoint. |
| `APP_STRIPE_CURRENCY` | Yes | Backend | Stripe currency, default `eur`. |
| `APP_STRIPE_WEBHOOK_MAX_PAYLOAD_BYTES` | Optional | Backend | Webhook payload size limit. |
| `APP_STRIPE_WEBHOOK_INVALID_RATE_LIMIT_MAX_ATTEMPTS` | Optional | Backend | Invalid webhook request rate-limit attempts. |
| `APP_STRIPE_WEBHOOK_INVALID_RATE_LIMIT_WINDOW_SECONDS` | Optional | Backend | Invalid webhook request rate-limit window. |
| `APP_STRIPE_WEBHOOK_INVALID_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS` | Optional | Backend | Invalid webhook limiter cleanup interval. |

### Booking

| Variable | Required | Scope | Purpose |
| -------- | -------- | ----- | ------- |
| `APP_HAIR_SALON_ID` | Optional with default | Backend | Active salon ID. Defaults to the Flyway-seeded singleton salon ID. |
| `APP_BOOKING_TIMEZONE` | Yes | Backend | Booking timezone, default `Europe/Dublin`. |
| `APP_BOOKING_HOLD_LIMITS_MAX_ACTIVE_PER_SLOT` | Optional | Backend | Maximum active holds per employee/date/time slot. |
| `APP_BOOKING_PUBLIC_HOLD_RATE_LIMIT_IP_MAX_ATTEMPTS` | Optional | Backend | Public hold limiter IP attempts. |
| `APP_BOOKING_PUBLIC_HOLD_RATE_LIMIT_IP_WINDOW_SECONDS` | Optional | Backend | Public hold limiter IP window. |
| `APP_BOOKING_PUBLIC_HOLD_RATE_LIMIT_DEVICE_MAX_ATTEMPTS` | Optional | Backend | Public hold limiter device attempts. |
| `APP_BOOKING_PUBLIC_HOLD_RATE_LIMIT_DEVICE_WINDOW_SECONDS` | Optional | Backend | Public hold limiter device window. |
| `APP_BOOKING_PUBLIC_HOLD_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS` | Optional | Backend | Public hold limiter cleanup interval. |
| `APP_BOOKING_PUBLIC_ACTION_RATE_LIMIT_IP_MAX_ATTEMPTS` | Optional | Backend | Public booking action IP attempts. |
| `APP_BOOKING_PUBLIC_ACTION_RATE_LIMIT_IP_WINDOW_SECONDS` | Optional | Backend | Public booking action IP window. |
| `APP_BOOKING_PUBLIC_ACTION_RATE_LIMIT_TARGET_MAX_ATTEMPTS` | Optional | Backend | Public booking action target attempts. |
| `APP_BOOKING_PUBLIC_ACTION_RATE_LIMIT_TARGET_WINDOW_SECONDS` | Optional | Backend | Public booking action target window. |
| `APP_BOOKING_PUBLIC_ACTION_RATE_LIMIT_DEVICE_MAX_ATTEMPTS` | Optional | Backend | Public booking action device attempts. |
| `APP_BOOKING_PUBLIC_ACTION_RATE_LIMIT_DEVICE_WINDOW_SECONDS` | Optional | Backend | Public booking action device window. |
| `APP_BOOKING_PUBLIC_ACTION_RATE_LIMIT_CLEANUP_INTERVAL_SECONDS` | Optional | Backend | Public booking action limiter cleanup interval. |

### Admin Bootstrap

| Variable | Required | Scope | Purpose |
| -------- | -------- | ----- | ------- |
| `APP_ADMIN_BOOTSTRAP_ENABLED` | Required setting | Backend | Enables the controlled first-admin setup or password rotation path. Keep false after use. |
| `APP_ADMIN_BOOTSTRAP_USERNAME` | Required when bootstrap enabled | Backend | Admin username to create, verify, or rotate. |
| `APP_ADMIN_BOOTSTRAP_PASSWORD` | Required when bootstrap enabled | Backend | Strong bootstrap admin password. |
| `APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE` | Required setting | Backend | Must be true only for intentional password rotation. |

### Deployment and CI

| Variable | Required | Scope | Purpose |
| -------- | -------- | ----- | ------- |
| `SPRING_PROFILES_ACTIVE` | Yes | Backend | Use `dev` locally and `prod` for production-like deployments. |
| `PORT` | Optional | Backend | HTTP port. Defaults to `8080`; Render may provide it. |
| `NVD_API_KEY` | Optional | CI/security scan | Reduces NVD rate limiting for OWASP Dependency Check. |
| `APP_ENV`, `RENDER`, `RAILWAY_ENVIRONMENT`, `FLY_APP_NAME`, `K_SERVICE`, `DYNO`, and similar hosting signals | Platform-provided | Backend validation | If present without the `prod` profile, startup fails fast. |

## Docker Notes

The backend Dockerfile builds with Eclipse Temurin Java 21 and runs the final image as a dedicated non-root `app` user.

Base image tags are not digest-pinned, so they should be reviewed, rebuilt, and updated regularly.
