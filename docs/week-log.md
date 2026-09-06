# Week log

One entry per week. Each week must end with a proof artifact: a test that fails without the
change, a benchmark number, or a dashboard screenshot. No artifact means the week did not happen.

## Week 0 — environment and scaffold (2026-09-06)

Verified rather than assumed. Findings that changed the plan:

| Expected | Actual |
|---|---|
| Install JDK 21 | Not needed. Zulu **25.0.4 LTS** already installed (16 JDKs on the box) |
| Fix global `JAVA_HOME` | Do **not**. It is JDK 8 for other work; scoped it per-project instead |
| Create `.wslconfig` for memory | Not needed. Docker already reports **31 GB** to the Linux VM |
| — | **Unexpected:** JDK 8's truststore lacks the corporate TLS root CA, so Gradle downloads fail with `PKIX path building failed`. Zulu 25 works. See ADR 0002 |
| — | Kubernetes is **not** enabled in Docker Desktop yet. Needed in Week 15, not before |

Done:
- Gradle 9.7.1 multi-module skeleton, Kotlin DSL, version catalog, Spring Boot 4.1.1 BOM.
- `platform/common-core` with the `Money` value type (ADR 0001).
- `services/wallet-service` bootable skeleton.
- `deploy/compose/docker-compose.yml` with MySQL 8.4.

**Proof artifact:** `./gradlew build` green, including `MoneyTest` (7 tests: exactness under
repeated addition, overflow throwing rather than wrapping, cross-currency rejection, scale-correct
rendering) and a Testcontainers-backed `contextLoads()` that starts a real MySQL container.
