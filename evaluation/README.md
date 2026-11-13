# Reproducible platform measurements

This harness runs the application's real HTTP server, JWT filter, membership guard, JPA repositories, Flyway migrations, Redis cache/holds/rate-limit primitive, and Kafka consumers. Stripe payment service calls are replaced by a test-only Mockito bean. No real payment or email is sent. The evaluation uncovered and fixes a production cache serialization defect: immutable record lists now use explicit per-cache JSON types, with Java time support and a versioned key prefix. Scheduling/hold behaviour is unchanged.

## Run

Prerequisites: JDK 21, Docker Compose v2, Python 3.10+. Use a disposable local environment; fixture accounts, services, and Redis keys are created. The test requires an empty organizations table; recreate disposable infrastructure before repeating. Ports 55432, 56379, and 59092 must be free.

```bash
docker compose -p booking-evaluation -f evaluation/compose.yml up -d --wait
cd backend
bash mvnw clean verify
bash mvnw test -Dtest=PlatformEvaluationTest -Dbooking.evaluate=true -Djacoco.skip=true \
  -Devaluation.jvmArgs=-Xmx512m -Dsurefire.reportsDirectory=target/evaluation-test-reports \
  -Devaluation.revision=$(git rev-parse HEAD)
cd ..
python evaluation/report.py
```

Read `backend/target/evaluation/platform.json` for measurements and `backend/target/evaluation/RESULTS.md` for the generated report. Normal unit-test coverage remains under `backend/target/site/jacoco/`; the opt-in integration evaluation runs separately without JaCoCo and writes its own Surefire reports. Clean test infrastructure after collecting results:

```bash
docker compose -p booking-evaluation -f evaluation/compose.yml down -v
```

The fresh fixture contains **20 organizations, 60 per-tenant membership users (OWNER/ADMIN/STAFF), 1,000 initial services, and 20 staff**. The RBAC matrix creates another 40 services via allowed writes. Fixture JWTs are issued with the application's token service; password login, registration, and password policy are outside this measurement.

## Measurements and denominators

- **RBAC / cross-tenant access:** 20 tenants × (OWNER, ADMIN, STAFF, another tenant's OWNER) × 6 endpoint/method combinations = **480 requests**. The combinations are GET services, POST services, GET staff, GET bookings, GET analytics, and GET audit logs. Exact expected 200/403 responses are asserted. Authorized catalogue response IDs must belong to the requested tenant. Another **1 unauthenticated request** is checked for rejection. This is a fixed matrix, not a proof that every endpoint is secure.
- **SQL reduction:** 100 direct repository catalogue reads versus 100 cached catalogue reads (1 cold + 99 warm) for the same tenant. Hibernate's prepared-statement counter is reset for each window; cache eviction and warmup are outside timing. The comparison counts statements, not a claimed API speedup. Service-call P50/P95 includes DB or Redis overhead but excludes HTTP and JWT checks.
- **HTTP read sampling:** 40 warmup requests, then 200 authenticated catalogue GET requests each at **1, 8, and 32 closed-loop workers**. P50/P95 cover the client-observed HTTP request/response interval. RPS is completed requests divided by batch wall time. The client/server share one machine, with real DB/cache and a small fixture; this is not saturation testing or proof of a maximum supported user count.
- **Identical-slot hold contention:** **10 rounds × 32 HTTP callers = 320 attempts** for identical tenant, staff, start **and end** timestamps. One success and 31 conflicts per round are asserted. The hold API's rate-limit ceiling is raised to 100,000 in the evaluation only, so this checks lock contention rather than a 429 ceiling. This does **not** test full persisted-booking oversell safety.
- **Fixed-window limiter:** 100 direct calls, 20 workers, one unique Redis counter key, threshold 20 / 60 seconds. Expect 20 accepts and 80 HTTP-status-429 exceptions. This tests the primitive, not each endpoint's configured quota or a failure between INCR and EXPIRE.
- **Concurrency diagnostics:** 10 rounds × 32 direct calls replaying one hold token; report how many consumers succeed. Also attempt two overlapping holds with different starts. These observations deliberately expose unsupported guarantees; they do not fail the harness simply because a known concurrency gap exists. They do not measure persisted booking duplicates.
- **Kafka fan-out:** publish 100 synthetic unique booking events with the real KafkaTemplate; wait for the notification listener spy and the real Redis analytics counter to observe 100 each. Notification calls still execute the actual logging implementation. Two consumer groups are used. Elapsed time includes broker/consumer startup effects and is not a stable per-event latency percentile. This does not prove email delivery, outbox reliability, exactly-once semantics, crash recovery, or duplicate-event idempotency.

Percentiles use nearest-rank sampling. Exceptions/unexpected HTTP statuses fail asserted scenarios rather than being dropped from latency data. Concurrency diagnostics are separately labeled observational results. Java version, OS, processor count, max heap, source revision, fixture size, and measurement timestamp are included in JSON.

## Coverage and claims

Use whole-backend and package-level JaCoCo results with their actual scope. The existing Maven gates apply only to service/controller packages; passing those gates does not mean whole-project coverage meets the same percentage. The evaluation itself must not inflate unit-test coverage.

These are synthetic local development measurements, not production traffic, business impact, or an independent security audit. Avoid claims of "zero double bookings", "supports thousands of users", "exactly-once events", or production P95 based on this harness.
