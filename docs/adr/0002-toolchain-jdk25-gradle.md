# ADR 0002 — JDK 25 via a project-scoped `JAVA_HOME`, built with Gradle

- Status: accepted
- Date: 2026-09-06
- Week: 0

## Context

This is a shared work machine. Its global `JAVA_HOME` points at
`C:\DEV_HOME\TOOLS\java\1.8.0_111` because other projects on the box need JDK 8, and changing it
globally would break them. Two concrete failures came out of that during setup:

1. The `gradlew` launcher runs on whatever `JAVA_HOME` says, *before* it reads
   `org.gradle.java.home`. Gradle 9 will not start on JDK 8.
2. The JDK 8 truststore does not contain this network's TLS-interception root CA, so downloading
   the Gradle distribution failed with `PKIX path building failed`. Zulu 25's truststore does
   contain it, so the same download succeeds from a newer JDK.

Sixteen JDKs are already installed; no new install was needed.

## Decision

- Target **Zulu JDK 25.0.4 LTS** (`C:/DEV_HOME/TOOLS/java/zulu25.36.205-ca-jdk25.0.4.1-win_x64`).
  Spring Boot 4.1 requires Java 17 minimum and supports up to 26; 25 is the current LTS and brings
  virtual threads, which Week 16 needs for the tuning work.
- Build with **Gradle 9.7.1** (wrapper, Kotlin DSL) and a version catalog in
  `gradle/libs.versions.toml`. PayPay lists Gradle first in its stack, and multi-module is cleaner
  in Gradle than in Maven.
- Pin the daemon JVM in `gradle.properties` via `org.gradle.java.home`, and provide
  `scripts/env.ps1` to set `JAVA_HOME` for the *current shell only*. **Never change global
  `JAVA_HOME`.**
- Depend on the Spring Boot BOM through Gradle's native `platform()` rather than the
  `io.spring.dependency-management` plugin, which is the approach Spring now recommends for Gradle
  and removes a plugin from the build.

## Consequences

- Good: the day job's JDK 8 setup is untouched.
- Good: `gradle.properties` covers IDE and CI invocations; the script covers fresh shells.
- Cost: a new shell must dot-source `scripts/env.ps1` before `./gradlew`, or the launcher fails
  with a confusing TLS error rather than a clear "wrong Java version" message. This is written on
  the README so it is not rediscovered painfully.
