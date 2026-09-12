# Security Notes

## Secrets and Configuration

- Runtime secrets are read from environment variables; committed config files use empty defaults or placeholders.
- JWT secrets are validated so empty, weak, placeholder, or undersized values do not start the application.
- `ConfigurationSafetyValidator` fails startup for unsafe production-like configurations, including wildcard CORS origins with credentials, invalid SameSite values, `SameSite=None` without secure cookies, localhost datasource/CORS values in the `prod` profile, and hosted-platform environment signals without the `prod` profile.
- `HairSalonConfigurationValidator` checks that configured `APP_HAIR_SALON_ID` exists in the migrated `hair_salon` table before the app serves traffic.
- Secret rotation guidance lives in [Security Secret Rotation](security-secret-rotation.md).

## Admin Authentication

- Admin login returns a JWT-backed `HttpOnly` cookie scoped to the admin API path.
- Unsafe admin cookie-based requests require the `X-Admin-CSRF` header.
- Production cookie settings should use `APP_AUTH_COOKIE_SECURE=true`.
- Use `APP_AUTH_COOKIE_SAME_SITE=None` only for cross-site HTTPS deployments.
- Admin bootstrap should stay disabled after first setup or controlled password rotation.

## CORS, Proxying, and Headers

- CORS must be configured with exact frontend origins, not wildcards.
- Trusted proxy CIDRs should only be configured for known reverse proxies or load balancers.
- When no trusted proxy CIDRs are configured, forwarded headers are not trusted for client IP resolution.
- Spring Security applies API security headers.
- Render static-site security headers for the frontend are configured in `render.yaml`.

## Rate Limiting and Single-Instance Design

- Login, public booking hold, public booking action, and invalid Stripe webhook request limits are implemented in memory.
- This is intentional for a single VPS or single application instance deployment.
- Because limiter state is process-local, horizontal scaling requires moving rate limiting to a shared store such as Redis/Bucket4j or enforcing it at the edge, WAF, or load balancer.
- Background schedulers are single-instance oriented unless external locking is added for multi-instance deployments.

## Stripe and Logging

- Stripe webhooks are verified with `APP_STRIPE_WEBHOOK_SECRET`.
- Webhook payload size and invalid webhook attempts are guarded.
- PaymentIntent identifiers and customer details are sanitized, masked, or hashed in operational logs.
- Sensitive log sanitization covers credentials, tokens, emails, phone numbers, and payment identifiers.
- Booking hold expiration timestamps in application logs are formatted as explicit UTC timestamps.

## Docker Runtime

- The backend image is built from Eclipse Temurin Java 21 base images.
- The runtime image creates and uses a dedicated non-root `app` user.
- Base image tags are not digest-pinned, so they should be reviewed, rebuilt, and updated regularly.
