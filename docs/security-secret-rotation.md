# Security Secret Rotation

This repository keeps JWT and Stripe credentials env-driven on purpose. Rotation is operational, not code-driven.

## First admin bootstrap

- New deployments no longer rely on a seeded `admin` account or a repository-known password hash.
- Keep `APP_ADMIN_BOOTSTRAP_ENABLED=false` by default in production configuration.
- For first-time provisioning on an empty database:
  - set `APP_ADMIN_BOOTSTRAP_ENABLED=true`
  - set `APP_ADMIN_BOOTSTRAP_USERNAME` to the admin username you want to keep
  - set `APP_ADMIN_BOOTSTRAP_PASSWORD` to a strong password
  - deploy once and confirm the admin can log in
  - set `APP_ADMIN_BOOTSTRAP_ENABLED=false` and deploy again

Notes:

- Bootstrap creation is intentionally limited to the first active admin account.
- If an active admin already exists, bootstrap may only verify that same username or rotate its password explicitly.
- Bootstrap should stay disabled after controlled use so startup does not remain a credential-management path in steady-state production.
- Remove `APP_ADMIN_BOOTSTRAP_USERNAME` and `APP_ADMIN_BOOTSTRAP_PASSWORD` from steady-state production config when your platform supports unsetting them.

## JWT secret rotation

- Configure `APP_JWT_SECRET` with a new strong random value.
- Deploy the backend with the new value.
- Current behavior: rotation invalidates already-issued admin JWTs immediately.
- Ask admins to sign in again after the deploy.
- Verify:
  - `POST /api/v1/public/auth/login` returns a token
  - authenticated admin requests succeed with the new token
  - older tokens are rejected with `401`

Notes:

- Generate at least 256 bits of randomness.
- Do not place JWT secrets in tracked config files.
- No previous-secret verification window is implemented in this phase, so rotation is an immediate cutover and requires a coordinated redeploy.

## Stripe key rotation

- Rotate `APP_STRIPE_SECRET_KEY` in Stripe and update the backend environment.
- Rotate `APP_STRIPE_WEBHOOK_SECRET` after updating the webhook endpoint signing secret in Stripe.
- Keep `APP_STRIPE_PUBLISHABLE_KEY` aligned with the active Stripe account/environment.
- Deploy the backend after updating the env vars.
- Verify:
  - checkout configuration still returns the publishable key
  - card authorization still succeeds
  - Stripe webhook calls are accepted

Notes:

- The backend verifies webhooks against one active signing secret at a time, so webhook secret rotation is also a coordinated cutover.
- Keep Stripe secret values out of logs, tracked config, and support screenshots.

## Database credential rotation

- Create a new database password or a replacement application user in PostgreSQL/your managed database.
- Update `SPRING_DATASOURCE_PASSWORD` and, if you switched users, `SPRING_DATASOURCE_USERNAME`.
- Deploy the backend with the new database credentials.
- Verify:
  - `GET /actuator/health` returns `UP`
  - admin login still succeeds
  - booking creation and payment synchronization still work
- Revoke the previous database password or remove the superseded database user after the new deployment is confirmed healthy.

Notes:

- Prefer creating a replacement database user during high-safety rotations so rollback remains possible until verification is complete.
- Do not keep old and new database passwords active longer than the validation window.
- Keep rotated database credentials out of tracked config, shell history, screenshots, and support tickets.

## Bootstrap admin credential rotation

- Set a new strong `APP_ADMIN_BOOTSTRAP_PASSWORD`.
- Set `APP_ADMIN_BOOTSTRAP_USERNAME` to the existing admin username you are rotating.
- Temporarily set `APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE=true`.
- Temporarily set `APP_ADMIN_BOOTSTRAP_ENABLED=true`.
- Deploy once and confirm the admin can log in with the new password.
- After verification, set `APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE=false`, set `APP_ADMIN_BOOTSTRAP_ENABLED=false`, and deploy again.

Notes:

- Password overwrite now increments `admin_user.token_version`, so existing admin JWTs are invalidated.
- Leave `APP_ADMIN_BOOTSTRAP_ENABLED` on only when you intentionally need bootstrap behavior in that environment.
- Keep `APP_ADMIN_BOOTSTRAP_ALLOW_PASSWORD_OVERWRITE=false` outside the short rotation window.

## Dependency scan

Run the explicit security scan profile when you want dependency vulnerability analysis:

```bash
cd backend
./mvnw -Psecurity-scan verify
```

Windows PowerShell:

```powershell
cd backend
.\mvnw.cmd -Psecurity-scan verify
```

Practical notes:

- The first run may take a while because OWASP Dependency-Check downloads NVD data.
- Set `NVD_API_KEY` to reduce rate limiting in local development or CI.
- The `security-scan` profile already fails on feed/update errors by default.
- Reports are written under `target/dependency-check/` relative to `backend/`.
