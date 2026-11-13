# Recorded local environment

The checked-in sample results were measured on Linux amd64, 2 reported processors, Temurin JDK 21.0.13, with a 512 MiB evaluation JVM heap. The HTTP client and Spring Boot server share this JVM and machine. PostgreSQL 16.16 and Redis 7.4.12 ran in disposable Docker containers. Apache Kafka 3.8.0 ran from the official `kafka_2.13-3.8.0.tgz` distribution in a host JVM with `-Xms128m -Xmx256m`, because this workspace's 8 GiB filesystem and Docker VFS layer duplication could not accommodate the Kafka container alongside the other infrastructure.

The standard reproduction and CI use `evaluation/compose.yml` with the same PostgreSQL/Redis major versions and Kafka 3.8.0 (256 MiB heap). Container placement, mutable patch-level image tags, CPU, caches, and process scheduling differ across machines, so latency samples are observations, not bit-for-bit reproducible capacity figures.

Local Kafka source: https://archive.apache.org/dist/kafka/3.8.0/kafka_2.13-3.8.0.tgz

The local KRaft broker used one node (`node.id=1`), `process.roles=broker,controller`, client listener `localhost:59092`, controller `localhost:59093`, one partition, replication factor 1, and `group.initial.rebalance.delay.ms=0`. No SASL, TLS, broker-failure or durability stress was involved. PostgreSQL used default READ_COMMITTED isolation; no database exclusion constraint was added.

The source revision supplied to the harness and the SHA-256 hashes in raw JSON identify the exact committed production cache and evaluation test sources used in the final run. Numerical results are copied from the harness without modification. SQL is counted by a caller-thread-local Hibernate StatementInspector; SQL issued by the web server or other background threads cannot pollute the direct catalogue comparison.

## Repeated-run SQL observation

The checked-in local sample measured 100 direct statements and 1 cached statement (zero in the warm window). Repeated GitHub container runs also observed 2 SQL statements in the warm window, even after counting was scoped to the caller thread. The harness now records any warm-window SQL text for diagnosis and reports measured query reduction instead of requiring exactly one cached statement in every environment. The local 99% figure is a recorded sample, not a universal cache-hit or SQL-elimination guarantee. Successful CI artifacts contain the per-run counts.
