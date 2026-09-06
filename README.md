# kessai (決済) — a QR payment platform

A deliberately production-shaped digital wallet, built to develop and demonstrate the things a
high-traffic payments backend actually requires: correctness under concurrency, distributed
caching, event-driven consistency, and graceful failure.

It grows over 18 weeks from one Spring Boot service into a Kafka/Redis/Kubernetes microservice
system. Each week adds one capability and leaves behind a proof artifact — a test that fails
without the change, or a benchmark number. See [`docs/week-log.md`](docs/week-log.md).

## The invariant

Every movement of money is a balanced double-entry journal. The signed sum of all ledger entries
is exactly zero, always, under any amount of concurrency. Everything else in this system is in
service of keeping that true while going fast.

Money is a `long` count of a currency's minor units. Never a `double`.
See [ADR 0001](docs/adr/0001-money-as-long-minor-units.md).

## Running it

**Every new shell must point at JDK 25 first.** This machine's global `JAVA_HOME` is JDK 8 for
other work and is deliberately left alone. Without this, `gradlew` fails with a confusing
`PKIX path building failed` TLS error rather than a clear version message
([ADR 0002](docs/adr/0002-toolchain-jdk25-gradle.md)):

```powershell
. .\scripts\env.ps1
```

Then:

```bash
docker compose -f deploy/compose/docker-compose.yml up -d
```

```bash
./gradlew build
```

## Layout

| Path | What lives there |
|---|---|
| `platform/common-core` | Framework-free domain primitives (`Money`, `Currency`) |
| `services/wallet-service` | Accounts and the double-entry ledger |
| `deploy/compose` | Local infrastructure (MySQL now; Redis and Kafka from Weeks 4 and 7) |
| `docs/adr` | Architecture decision records — one per real decision, with its cost |
| `docs/drills` | Weekly system design exercises |
| `docs/benchmarks` | Load-test results, measured against the Week 3 baseline |
| `loadtest` | k6 scripts |

## Stack

Java 25 (Zulu LTS) · Spring Boot 4.1 · Gradle 9.7 · MySQL 8.4 · Flyway · Testcontainers.
Redis, Kafka, Resilience4j, Prometheus/Grafana, OpenTelemetry and Kubernetes arrive in later
phases.
