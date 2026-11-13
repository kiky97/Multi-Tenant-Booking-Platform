# Measured platform results

Measured source snapshot: `20750060d0afadbb58cead744d641b007d4ce550`. Execution label: `736dc89-harness-updates`.

Measured at 2026-11-13T22:44:26.508422044Z; Java 21.0.13, Linux amd64, 2 reported processors, max JVM heap 512 MiB.

Local synthetic evaluation; real PostgreSQL 16, Redis 7, Kafka 3.8.0. The application and load generator share one JVM/host; Stripe is mocked and notifications log only. See [methodology](../../evaluation/README.md).

Fixture: 20 tenants, 60 membership users, 1,000 initial services, 20 staff. Allowed RBAC writes add 40 service records. Tokens use real JWT signing/parsing; login is outside this test.

## Correctness and query reduction

| Measurement | Observed result |
|---|---|
| Role/tenant matrix | 480/480 expected responses |
| Cross-tenant owner requests | 120/120 rejected with 403 |
| Unauthenticated catalogue request | Rejected with 403 |
| Catalogue SQL statements / 100 reads | 100 direct → 1 cached (99% fewer statements) |
| Identical-slot holds (10 rounds, 32 workers) | 10 accepts, 310 conflicts / 320 attempts |
| Redis limiter (100 attempts, threshold 20) | 20 allowed, 80 rejected |
| Kafka fan-out | 100 published; 100 notification calls + 100 analytics increments, 2 groups |

The hold result applies only to identical start/end keys. The SQL reduction is a service-catalogue query count comparison with 99 warm hits, not a measured end-to-end speedup.

## Authenticated HTTP catalogue reads

| Closed-loop workers | Requests | HTTP 200 | P50 ms | P95 ms | Observed req/s |
|---:|---:|---:|---:|---:|---:|
| 1 | 200 | 200 | 5.23 | 18.11 | 127.1 |
| 8 | 200 | 200 | 29.92 | 60.88 | 210.4 |
| 32 | 200 | 200 | 88.97 | 138.89 | 329.5 |

40 warmup requests precede these 600 timed reads. Client HTTP interval includes JWT, membership lookup, cached catalogue access, serialization, and local network. These are short healthy-run samples, not maximum capacity, production P95, or evidence of user concurrency support.

## Direct catalogue-call timing

| Path (100 reads) | P50 ms | P95 ms |
|---|---:|---:|
| direct | 1.362 | 2.328 |
| cached | 1.090 | 3.943 |

Direct DB reads and cached service reads are different execution paths; no API latency improvement is inferred from them.

## Concurrency limitations observed

- Replaying one token with 32 simultaneous Redis service callers produced successful-consume counts **[3, 8, 5, 2, 3, 19, 7, 17, 8, 2]** across ten rounds; **10/10** rounds had more than one success.
- A second overlapping hold with a different start timestamp was accepted: **True**.

These are diagnostic observations, not passing guarantees. `consume` uses multiple Redis reads/deletes rather than an atomic compare-and-delete; slot keys encode exact start/end, not an interval overlap index. A database check-then-insert is also not a database exclusion constraint. **Do not claim zero double booking or replay-safe booking confirmation.** Persisted booking duplicates, payment interactions, TTL expiry races, and crash recovery were not measured here.

## Event scope

Both consumers observed 100 events within 650.6 ms of publication start in this run. This includes startup effects and is not a per-event P95.

No live Stripe webhook flow, actual email, duplicate-event idempotency, durable outbox, broker failure, or exactly-once guarantee is established.

## Unit-test coverage (separate from the integration evaluation)

| Scope | Line coverage | Branch coverage |
|---|---:|---:|
| Whole backend | 338/638 (53.0%) | 81/128 (63.3%) |
| security package | 16/42 (38.1%) | 3/12 (25.0%) |
| service package | 131/166 (78.9%) | 43/52 (82.7%) |
| controller package | 126/298 (42.3%) | 32/58 (55.2%) |

Normal backend run: 47 discovered, 0 failures, 0 errors, 1 skipped. Evaluation runs separately with JaCoCo disabled.

Separately recorded local frontend verification: 21 Vitest tests passed; ESLint and Vite production build passed. This renderer does not rerun frontend checks. Dependency security scans are outside these measurements.

Raw evidence: [platform.json](platform.json). CI uploads integration measurements, Surefire reports, and separate unit-test coverage.

For checked-in local results, see [environment details and source provenance](ENVIRONMENT.md).
