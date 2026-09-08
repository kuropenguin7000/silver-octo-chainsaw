# kessai

Training project for a PayPay Japan senior backend engineer application.
Plan and specs: `docs/week-01-spec.md`, `docs/week-log.md`, `docs/adr/`.

## Comments

**Comment only what the code cannot say itself.** Default to none.

Write one only when a future reader would otherwise delete or "fix" the line: a non-obvious
constraint, a subtle ordering dependency, a security reason, a magic number, a workaround.

Do not write:
- Javadoc on DTOs, records, enums, repositories, getters, or config classes
- Class-level javadoc that restates the class name
- Comments on tests whose name already says it
- Rationale that belongs in an ADR or a spec — link to it instead

## Conventions

- Package by feature (`user/`, `wallet/`, `ledger/`), never by layer
- `@Transactional` on service methods only, never controllers
- Entities: private constructor + static factory, getters only, no setters
- DTOs are records; request-DTO size limits mirror the column widths
- A new failure mode is one constant in `ErrorCode` and nothing else
- Flyway owns the schema (`ddl-auto: validate`) — see `docs/database-migrations.md`

## Environment

- Global `JAVA_HOME` is JDK 8 for unrelated work. **Never change it.** Run `. .\scripts\env.ps1`
  first, or Gradle fails with a confusing `PKIX path building failed` TLS error (ADR 0002)
- Java 25 (Zulu), Gradle 9.7, Spring Boot 4.1, MySQL 8.4

## Spring Boot 4 differences

Most references online are still Boot 3 and will be wrong:

| Boot 3 | Boot 4 |
|---|---|
| `...boot.test.autoconfigure.web.servlet.*` | `org.springframework.boot.webmvc.test.autoconfigure.*` |
| `com.fasterxml.jackson.databind.*` | `tools.jackson.databind.*` (Jackson 3) |
| `JsonNode.asText()` | `JsonNode.asString()` |
| `@MockBean` | `@MockitoBean` |
| `spring-boot-starter-web` | `spring-boot-starter-webmvc` |
| springdoc 2.x | springdoc 3.1.0 |

`MySQLContainer` is no longer generic.

## Git

**Never commit or push.** The user does that manually.
