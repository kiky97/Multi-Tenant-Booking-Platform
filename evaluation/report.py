#!/usr/bin/env python3
"""Render real measurements; do not infer production capacity from this report."""
import json
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
TARGET = ROOT / 'backend/target/evaluation'
r = json.loads((TARGET / 'platform.json').read_text())
required = ['tenantRbac', 'cache', 'httpReads', 'sameSlotHolds', 'fixedWindowLimiter', 'knownConcurrencyDiagnostics', 'kafka']
assert all(k in r for k in required), 'Evaluation incomplete; inspect test failures before publishing results'
lines = ['# Measured platform results', '', f"Measured source snapshot: `{r.get('sourceSnapshotRevision', r['revision'])}`. Execution label: `{r['revision']}`.", '',
         f"Measured at {r['measuredAt']}; Java {r['java']}, {r['os']}, {r['processors']} reported processors, max JVM heap {r['jvmMaxHeapMb']} MiB.", '',
         'Local synthetic evaluation; real PostgreSQL 16, Redis 7, Kafka 3.8.0. The application and load generator share one JVM/host; Stripe is mocked and notifications log only. See [methodology](../../evaluation/README.md).', '',
         'Fixture: 20 tenants, 60 membership users, 1,000 initial services, 20 staff. Allowed RBAC writes add 40 service records. Tokens use real JWT signing/parsing; login is outside this test.', '',
         '## Correctness and query reduction', '', '| Measurement | Observed result |', '|---|---|']
m = r['tenantRbac']; c = r['cache']; h = r['sameSlotHolds']; rate = r['fixedWindowLimiter']; k = r['kafka']; diag = r['knownConcurrencyDiagnostics']
foreign = [x for x in m['cases'] if x['role'] == 'FOREIGN_OWNER']
lines += [f"| Role/tenant matrix | {m['passed']}/{m['matrixRequests']} expected responses |",
          f"| Cross-tenant owner requests | {sum(x['actual'] == 403 for x in foreign)}/{len(foreign)} rejected with 403 |",
          f"| Unauthenticated catalogue request | Rejected with {m['unauthenticatedRequestStatus']} |",
          f"| Catalogue SQL statements / 100 reads | {c['directSqlStatements']} direct → {c['cachedSqlStatements']} cached ({(1-c['cachedSqlStatements']/c['directSqlStatements'])*100:.0f}% fewer statements) |",
          f"| Identical-slot holds (10 rounds, 32 workers) | {h['acquired']} accepts, {h['conflicts']} conflicts / {h['totalRequests']} attempts |",
          f"| Redis limiter (100 attempts, threshold 20) | {rate['allowed']} allowed, {rate['rejected']} rejected |",
          f"| Kafka fan-out | {k['published']} published; {k['notificationListenerInvocations']} notification calls + {k['analyticsRedisCount']} analytics increments, 2 groups |", '',
          'The hold result applies only to identical start/end keys. The SQL reduction is a service-catalogue query count comparison with 99 warm hits, not a measured end-to-end speedup.', '',
          '## Authenticated HTTP catalogue reads', '', '| Closed-loop workers | Requests | HTTP 200 | P50 ms | P95 ms | Observed req/s |', '|---:|---:|---:|---:|---:|---:|']
for row in r['httpReads']:
    lines.append(f"| {row['workers']} | {row['requests']} | {row['successes']} | {row['latencyMs']['p50']:.2f} | {row['latencyMs']['p95']:.2f} | {row['observedRequestsPerSecond']:.1f} |")
lines += ['', '40 warmup requests precede these 600 timed reads. Client HTTP interval includes JWT, membership lookup, cached catalogue access, serialization, and local network. These are short healthy-run samples, not maximum capacity, production P95, or evidence of user concurrency support.', '',
          '## Direct catalogue-call timing', '', '| Path (100 reads) | P50 ms | P95 ms |', '|---|---:|---:|']
for path in ['direct','cached']:
    t = c[path + 'InProcessMs']
    lines.append(f"| {path} | {t['p50']:.3f} | {t['p95']:.3f} |")
lines += ['', 'Direct DB reads and cached service reads are different execution paths; no API latency improvement is inferred from them.', '',
          '## Concurrency limitations observed', '',
          f"- Replaying one token with 32 simultaneous Redis service callers produced successful-consume counts **{diag['consumeReplaySuccessfulCallsPerRound']}** across ten rounds; **{diag['consumeReplayRoundsWithMultipleSuccesses']}/10** rounds had more than one success.",
          f"- A second overlapping hold with a different start timestamp was accepted: **{diag['overlappingDifferentStartHoldAccepted']}**.", '',
          'These are diagnostic observations, not passing guarantees. `consume` uses multiple Redis reads/deletes rather than an atomic compare-and-delete; slot keys encode exact start/end, not an interval overlap index. A database check-then-insert is also not a database exclusion constraint. **Do not claim zero double booking or replay-safe booking confirmation.** Persisted booking duplicates, payment interactions, TTL expiry races, and crash recovery were not measured here.', '',
          '## Event scope', '',
          f"Both consumers observed 100 events within {k['elapsedUntilBothObservedMs']:.1f} ms of publication start in this run. This includes startup effects and is not a per-event P95.", '',
          'No live Stripe webhook flow, actual email, duplicate-event idempotency, durable outbox, broker failure, or exactly-once guarantee is established.', '',
          '## Unit-test coverage (separate from the integration evaluation)', '']
coverage = ET.parse(ROOT / 'backend/target/site/jacoco/jacoco.xml').getroot()
lines += ['| Scope | Line coverage | Branch coverage |', '|---|---:|---:|']
for name, node in [('Whole backend', coverage)] + [(p.get('name').rsplit('/',1)[-1] + ' package',p) for p in coverage.findall('package') if p.get('name').endswith(('/service','/controller','/security'))]:
    counters = {x.get('type'):x for x in node.findall('counter')}
    values = []
    for kind in ['LINE','BRANCH']:
        counter = counters[kind]; covered = int(counter.get('covered')); total = covered + int(counter.get('missed'))
        values.append(f'{covered}/{total} ({covered/total*100:.1f}%)' if total else 'N/A')
    lines.append('| ' + name + ' | ' + ' | '.join(values) + ' |')
counts = {key: 0 for key in ['tests','failures','errors','skipped']}
for file in (ROOT/'backend/target/surefire-reports').glob('TEST-*.xml'):
    suite = ET.parse(file).getroot()
    for key in counts: counts[key] += int(suite.get(key,0))
lines += ['', f"Normal backend run: {counts['tests']} discovered, {counts['failures']} failures, {counts['errors']} errors, {counts['skipped']} skipped. Evaluation runs separately with JaCoCo disabled.", '',
          'Separately recorded local frontend verification: 21 Vitest tests passed; ESLint and Vite production build passed. This renderer does not rerun frontend checks. Dependency security scans are outside these measurements.', '',
          'Raw evidence: [platform.json](platform.json). CI uploads integration measurements, Surefire reports, and separate unit-test coverage.']
text = '\n'.join(lines) + '\n'
(TARGET/'RESULTS.md').write_text(text)
print(text)
