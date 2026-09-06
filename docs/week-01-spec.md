# Week 1 — "Open a wallet and put money in it"

**Goal:** a correct, single-node wallet you can create, fund, and read the balance of.
**Explicitly not in scope:** transfers, concurrency, locking, idempotency, auth, caching, events.
**Time:** 5 × 2h weekday sessions + Saturday.

---

## 1. The feature, in one paragraph

A new user signs up. They get a JPY wallet. They top it up from their bank. They check their
balance and see their transaction history. Nothing is spent yet — spending is Week 2.

This slice is deliberately boring at the surface and load-bearing underneath. It forces every
structural decision the next 17 weeks depend on (how money is represented, how the ledger records
it, how errors are shaped, where package boundaries fall) while having **zero concurrency**. When
Week 2 introduces contention, you will be changing exactly one variable.

---

## 2. Design decisions to make consciously

These are the ones an interviewer will actually dig into. Write ADR 0003 covering 1–3 on Saturday.

### 2.1 Signed amounts, not debit/credit columns

Each journal entry has one signed `amount_minor`: **positive means money moved *into* the account,
negative means *out of* it.** A ¥10,000 top-up produces exactly two entries:

| account | amount_minor |
|---|---|
| `EXTERNAL_FUNDING` (system) | `-10000` |
| user's `USER_BALANCE` | `+10000` |
| **sum** | **`0`** |

The alternative — separate `debit` and `credit` columns, both positive — is what accountants
actually use, and it makes the "which side is this?" question explicit. But it turns the balance
invariant into a two-column expression and every query into a `CASE`. Signed amounts make the
invariant a plain `SUM()`, which is why this codebase uses them. Know both; be able to say why you
picked one.

> **The system account will go negative and that is correct.** `EXTERNAL_FUNDING` is a *source*.
> Money flowing into the platform means that account descends. It is not a bug. If it were
> positive you would be creating money from nothing.

### 2.2 Balance is stored *and* derived

`account.balance_minor` is a materialised column, updated in the same transaction as the entries.
Reads use it, because summing a million rows on every balance check is not viable.

But the truth is `SELECT SUM(amount_minor) FROM journal_entry WHERE account_id = ?`. So a test
asserts the two agree.

This is deliberate scaffolding: **Week 2's concurrent-transfer test will make them disagree**, and
watching that happen is the entire lesson about lost updates. Do not add locking now to prevent it.

### 2.3 The journal is append-only

`journal_entry` is never UPDATEd and never DELETEd. A mistake is corrected by writing a new
compensating entry, not by editing history. Do not put update or delete methods on its repository —
the absence is the enforcement.

### 2.4 Package by feature, not by layer

```
io.kessai.wallet.user.*      NOT   io.kessai.wallet.controller.*
io.kessai.wallet.wallet.*                              .service.*
io.kessai.wallet.ledger.*                              .repository.*
```

If your top-level packages are `controller` / `service` / `repository`, then splitting into
microservices in Week 11 means touching every package you own. If they are `user` / `wallet` /
`ledger`, you cut along seams that already exist. This is a five-minute decision now and a two-day
one in December.

### 2.5 IDs are UUIDv7 in BINARY(16)

- **Not auto-increment BIGINT:** leaks transaction volume to anyone who can see an ID, and becomes
  a resharding problem in Week 14.
- **Not UUIDv4:** random values scatter inserts across the InnoDB clustered index, causing page
  splits and write amplification on a table that only ever grows.
- **UUIDv7** is time-ordered in its high bits, so inserts stay sequential like an auto-increment,
  while remaining globally unique and shardable.
- **BINARY(16) not CHAR(36):** 16 bytes vs 36, and every secondary index carries a copy of the PK.

Read rows in MySQL with `SELECT HEX(id) FROM ...`.

### 2.6 Money on the wire is minor units, never a JSON number with a decimal point

```json
"amount": { "minorUnits": 10000, "currency": "JPY" }
```

JSON has one number type, and every JavaScript client parses it as an IEEE-754 double. Sending
`100.50` invites precision loss at the boundary of a system whose entire job is not losing money.
Integer minor units plus an explicit currency cannot be misread.

### 2.7 Errors are RFC 9457 Problem Details

Spring has `ProblemDetail` built in. Use `application/problem+json` and one shape for every error.

---

## 3. Schema

Four Flyway migrations in `services/wallet-service/src/main/resources/db/migration/`.
Flyway owns the schema; `ddl-auto: validate` makes Hibernate check it and never write it.

### V1__create_identity.sql

```sql
CREATE TABLE app_user (
    id           BINARY(16)   NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    email        VARCHAR(255) NOT NULL,
    status       VARCHAR(20)  NOT NULL,
    created_at   DATETIME(6)  NOT NULL,
    updated_at   DATETIME(6)  NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_app_user_email (email)
) ENGINE = InnoDB;
```

`user` is a reserved word in MySQL 8 — hence `app_user`.
`version` is unused this week; Week 2 turns it into optimistic locking.

### V2__create_wallet_and_account.sql

```sql
CREATE TABLE wallet (
    id         BINARY(16)  NOT NULL,
    user_id    BINARY(16)  NOT NULL,
    currency   CHAR(3)     NOT NULL,
    status     VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    version    BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_wallet_user_currency (user_id, currency),
    CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB;

CREATE TABLE account (
    id            BINARY(16)  NOT NULL,
    wallet_id     BINARY(16)  NULL,
    system_key    VARCHAR(64) NULL,
    account_type  VARCHAR(32) NOT NULL,
    currency      CHAR(3)     NOT NULL,
    balance_minor BIGINT      NOT NULL DEFAULT 0,
    status        VARCHAR(20) NOT NULL,
    created_at    DATETIME(6) NOT NULL,
    updated_at    DATETIME(6) NOT NULL,
    version       BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uq_account_wallet_type (wallet_id, account_type),
    UNIQUE KEY uq_account_system_key (system_key),
    CONSTRAINT fk_account_wallet FOREIGN KEY (wallet_id) REFERENCES wallet (id),
    CONSTRAINT ck_account_owner CHECK (
        (wallet_id IS NOT NULL AND system_key IS NULL) OR
        (wallet_id IS NULL AND system_key IS NOT NULL)
    )
) ENGINE = InnoDB;
```

`uq_wallet_user_currency` is the real rule: **one wallet per user per currency.** Enforce it in the
database, not only in service code — the database is the only place that holds under concurrency.

`ck_account_owner` says every account belongs to exactly one of a wallet or the system, never both
and never neither. `uq_account_wallet_type` gives a wallet at most one account of each type.
MySQL treats NULLs as distinct in unique keys, which is why system accounts need their own
`system_key` unique column rather than relying on that index.

### V3__create_ledger.sql

```sql
CREATE TABLE ledger_transaction (
    id               BINARY(16)   NOT NULL,
    transaction_type VARCHAR(32)  NOT NULL,
    currency         CHAR(3)      NOT NULL,
    description      VARCHAR(255) NULL,
    created_at       DATETIME(6)  NOT NULL,
    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE journal_entry (
    id             BINARY(16)  NOT NULL,
    transaction_id BINARY(16)  NOT NULL,
    account_id     BINARY(16)  NOT NULL,
    amount_minor   BIGINT      NOT NULL,
    currency       CHAR(3)     NOT NULL,
    created_at     DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_journal_entry_account (account_id, created_at),
    KEY idx_journal_entry_transaction (transaction_id),
    CONSTRAINT fk_journal_entry_transaction
        FOREIGN KEY (transaction_id) REFERENCES ledger_transaction (id),
    CONSTRAINT fk_journal_entry_account
        FOREIGN KEY (account_id) REFERENCES account (id),
    CONSTRAINT ck_journal_entry_nonzero CHECK (amount_minor <> 0)
) ENGINE = InnoDB;
```

`ledger_transaction` groups the entries that must balance. Without it, "these two rows are two
sides of one movement" lives only in your head.

`idx_journal_entry_account (account_id, created_at)` is the index that makes "this account's
history, newest first" cheap. Composite order matters: equality column first, range/sort column
second.

### V4__seed_system_accounts.sql

```sql
INSERT INTO account
    (id, wallet_id, system_key, account_type, currency,
     balance_minor, status, created_at, updated_at, version)
VALUES
    (UNHEX(REPLACE('00000000-0000-7000-8000-000000000001', '-', '')),
     NULL, 'EXTERNAL_FUNDING_JPY', 'EXTERNAL_FUNDING', 'JPY',
     0, 'ACTIVE', NOW(6), NOW(6), 0);
```

A fixed, deterministic ID so tests and code can refer to it without a lookup by string.

---

## 4. Package layout

```
io.kessai.wallet
├── WalletServiceApplication.java
├── shared/
│   ├── id/Uuid7.java
│   └── error/
│       ├── ApiExceptionHandler.java     @RestControllerAdvice
│       ├── DomainException.java         base, carries an ErrorCode
│       ├── ErrorCode.java               enum: code + HTTP status + title
│       ├── NotFoundException.java
│       ├── ConflictException.java
│       └── UnprocessableException.java
├── user/
│   ├── UserController.java   UserService.java
│   ├── User.java  UserStatus.java  UserRepository.java
│   └── dto/CreateUserRequest.java  dto/UserResponse.java
├── wallet/
│   ├── WalletController.java   WalletService.java
│   ├── Wallet.java  WalletStatus.java  WalletRepository.java
│   └── dto/CreateWalletRequest.java  dto/WalletResponse.java  dto/TopUpRequest.java  …
└── ledger/
    ├── LedgerService.java
    ├── Account.java  AccountType.java  AccountRepository.java
    ├── LedgerTransaction.java  JournalEntry.java  TransactionType.java
    └── JournalEntryRepository.java
```

`@Transactional` goes on **service** methods, never on controllers.

---

## 5. API

Base path `/api/v1`. Responses are `application/json`; errors are `application/problem+json`.

### 5.1 POST /api/v1/users — create a user

```json
{ "displayName": "Rahman", "email": "rahman@example.com" }
```

Validation: `displayName` `@NotBlank @Size(max=100)`; `email` `@NotBlank @Email @Size(max=255)`.

**201 Created**, header `Location: /api/v1/users/{id}`

```json
{
  "id": "01927f3a-8c21-7c4e-9b3d-1a2b3c4d5e6f",
  "displayName": "Rahman",
  "email": "rahman@example.com",
  "status": "ACTIVE",
  "createdAt": "2026-09-08T09:15:32.481Z"
}
```

| Failure | Status | code |
|---|---|---|
| blank name / malformed email | `400` | `VALIDATION_FAILED` |
| email already registered | `409` | `USER_EMAIL_TAKEN` |

### 5.2 GET /api/v1/users/{userId}

`200` with the object above, or `404` `USER_NOT_FOUND`.

### 5.3 POST /api/v1/users/{userId}/wallets — open a wallet

```json
{ "currency": "JPY" }
```

**201 Created**

```json
{
  "id": "01927f3b-1d55-7a02-8e77-9f0011223344",
  "userId": "01927f3a-8c21-7c4e-9b3d-1a2b3c4d5e6f",
  "currency": "JPY",
  "status": "ACTIVE",
  "balance": { "minorUnits": 0, "currency": "JPY" },
  "createdAt": "2026-09-08T09:16:04.112Z"
}
```

**Side effect, same transaction:** creating a wallet also creates its `USER_BALANCE` account. A
wallet without its account is a broken state that must never be observable.

| Failure | Status | code |
|---|---|---|
| unsupported currency | `400` | `VALIDATION_FAILED` |
| user does not exist | `404` | `USER_NOT_FOUND` |
| wallet already exists in that currency | `409` | `WALLET_ALREADY_EXISTS` |

### 5.4 GET /api/v1/wallets/{walletId}

`200` with the wallet object (balance read from `account.balance_minor`), or `404`
`WALLET_NOT_FOUND`.

### 5.5 POST /api/v1/wallets/{walletId}/topups — fund the wallet

```json
{
  "amount": { "minorUnits": 10000, "currency": "JPY" },
  "reference": "bank-transfer-abc123"
}
```

Validation: `minorUnits` `@Positive`; `currency` `@NotNull`; `reference` optional `@Size(max=64)`.

**201 Created**

```json
{
  "transactionId": "01927f3c-77aa-7b19-9c02-556677889900",
  "walletId": "01927f3b-1d55-7a02-8e77-9f0011223344",
  "type": "TOP_UP",
  "amount":       { "minorUnits": 10000, "currency": "JPY" },
  "balanceAfter": { "minorUnits": 10000, "currency": "JPY" },
  "createdAt": "2026-09-08T09:17:41.903Z"
}
```

What must happen inside one transaction:

1. insert `ledger_transaction` (`TOP_UP`)
2. insert `journal_entry` −10000 against `EXTERNAL_FUNDING_JPY`
3. insert `journal_entry` +10000 against the wallet's `USER_BALANCE`
4. `UPDATE account SET balance_minor = balance_minor + ?` for both

| Failure | Status | code |
|---|---|---|
| amount ≤ 0 | `400` | `VALIDATION_FAILED` |
| wallet does not exist | `404` | `WALLET_NOT_FOUND` |
| wallet not ACTIVE | `409` | `WALLET_NOT_ACTIVE` |
| request currency ≠ wallet currency | `422` | `CURRENCY_MISMATCH` |

> **This endpoint is not idempotent yet, and that is on purpose.** Retry it and the user is funded
> twice. Week 3 adds `Idempotency-Key`. Feel the bug first — resist fixing it early, because the
> whole point of Week 3 is having a reproduction to fix.

### 5.6 GET /api/v1/wallets/{walletId}/entries?page=0&size=20

```json
{
  "walletId": "01927f3b-1d55-7a02-8e77-9f0011223344",
  "balance": { "minorUnits": 10000, "currency": "JPY" },
  "entries": [
    {
      "entryId": "01927f3c-8801-7f33-aa10-0099aabbccdd",
      "transactionId": "01927f3c-77aa-7b19-9c02-556677889900",
      "type": "TOP_UP",
      "amount": { "minorUnits": 10000, "currency": "JPY" },
      "createdAt": "2026-09-08T09:17:41.903Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

---

## 6. Error shape

```json
{
  "type": "https://kessai.local/problems/currency-mismatch",
  "title": "Currency mismatch",
  "status": 422,
  "detail": "Wallet 01927f3b-1d55-7a02-8e77-9f0011223344 holds JPY but the request was for USD",
  "instance": "/api/v1/wallets/01927f3b-1d55-7a02-8e77-9f0011223344/topups",
  "code": "CURRENCY_MISMATCH",
  "timestamp": "2026-09-08T09:17:41.903Z"
}
```

`title` is stable and human-facing. `detail` is specific to this occurrence. `code` is the stable
machine-readable string clients branch on — **never make clients parse `detail`.**

Status selection, which is worth being able to defend:

| Situation | Status | Reasoning |
|---|---|---|
| Malformed request | `400` | Wrong before any domain logic runs |
| Resource absent | `404` | — |
| Uniqueness / state conflict | `409` | Request is valid but conflicts with current state; retrying it unchanged will fail again |
| Semantically invalid | `422` | Syntax is fine, meaning is not (currency mismatch, insufficient funds) |
| Anything unexpected | `500` | Log with a correlation ID; **never leak the exception message** |

`409` vs `422` is a common interview probe. The line used here: **409 is about state, 422 is about
meaning.**

---

## 7. Configuration

`services/wallet-service/src/main/resources/application.yml`:

```yaml
spring:
  application:
    name: wallet-service
  datasource:
    url: jdbc:mysql://localhost:3306/kessai_wallet?useSSL=false&allowPublicKeyRetrieval=true
    username: kessai
    password: kessai
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
    properties:
      hibernate:
        format_sql: true
  flyway:
    enabled: true
    locations: classpath:db/migration

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

Two settings that matter more than they look:

- **`ddl-auto: validate`** — Flyway owns the schema. `update` will silently diverge your dev
  database from your migrations and you will not notice until deployment.
- **`open-in-view: false`** — the default `true` keeps a Hibernate session open through view
  rendering, which hides lazy-loading mistakes in dev and produces N+1 queries in production. Turn
  it off and let the mistakes fail loudly in tests.

Add to `services/wallet-service/build.gradle.kts`:

```kotlin
implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:3.1.0")
```

**Version matters:** springdoc `2.x` is Spring Boot 3 only. Boot 4 needs `3.1.0` (verified on
Maven Central). Most tutorials you find will show `2.x`.

---

## 8. The fiddly bit, so you do not lose an evening to it

UUIDv7 bit-packing is not a skill anyone will interview you on — the *reasoning* in §2.5 is. Take
this and move on (or use `com.fasterxml.uuid:java-uuid-generator` if you prefer a library):

```java
package io.kessai.wallet.shared.id;

import java.security.SecureRandom;
import java.util.UUID;

/** UUIDv7: a 48-bit millisecond timestamp in the high bits, so IDs sort by creation time. */
public final class Uuid7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Uuid7() {}

    public static UUID generate() {
        byte[] rand = new byte[10];
        RANDOM.nextBytes(rand);

        long msb = (System.currentTimeMillis() & 0xFFFF_FFFF_FFFFL) << 16;
        msb |= 0x7000L;                                        // version 7
        msb |= ((rand[0] & 0x0FL) << 8) | (rand[1] & 0xFFL);   // 12 random bits

        long lsb = 0;
        for (int i = 2; i < 10; i++) {
            lsb = (lsb << 8) | (rand[i] & 0xFFL);
        }
        lsb &= 0x3FFF_FFFF_FFFF_FFFFL;
        lsb |= 0x8000_0000_0000_0000L;                         // RFC 4122 variant

        return new UUID(msb, lsb);
    }
}
```

Map it to `BINARY(16)` on entities with:

```java
@Id
@JdbcTypeCode(SqlTypes.BINARY)
private UUID id;
```

---

## 9. Suggested day plan

| Day | 2h |
|---|---|
| Mon | The four Flyway migrations. `docker compose up -d`, boot the app, confirm Flyway applied them. Inspect with `SELECT HEX(id), ... FROM account`. |
| Tue | JPA entities + repositories + `Uuid7`. One Testcontainers repository test that saves a `User` and reads it back. |
| Wed | `user` and `wallet` features end to end: service, controller, DTOs, Bean Validation. |
| Thu | `LedgerService` and top-up. The double-entry write. The invariant test. |
| Fri | `ApiExceptionHandler` + Problem Details + `ErrorCode`. Wire springdoc, check Swagger UI. |
| Sat | Entries endpoint, integration-test sweep, ADR 0003, week-log entry. Then drill 1 (URL shortener) + DDIA ch. 1. |

---

## 10. Definition of done

- [ ] `./gradlew build` green
- [ ] Integration test (Testcontainers): create user → open wallet → top up ¥10,000 → `GET` wallet
      reports ¥10,000
- [ ] `LedgerInvariantTest`: after N top-ups across several wallets,
      `SELECT SUM(amount_minor) FROM journal_entry` is **exactly 0**, and every
      `account.balance_minor` equals the `SUM` of that account's own entries
- [ ] Every failure row in the tables above has a test asserting its status **and** its `code`
- [ ] Swagger UI loads at `http://localhost:8080/swagger-ui.html`
- [ ] `docs/adr/0003-double-entry-ledger-design.md` written
- [ ] `docs/week-log.md` updated with the proof artifact

The invariant test is the one that matters. It is the thing you will still be running in Week 16,
and the thing you will describe in an interview.

---

## 11. Gotchas, ranked by how long they would otherwise cost you

1. **springdoc must be `3.1.0`.** `2.x` fails on Boot 4.
2. **Spring Boot 4 renamed the starters** — `spring-boot-starter-webmvc` (not `-web`),
   `spring-boot-starter-flyway`. Tutorials will show the 3.x names.
3. **`user` is reserved in MySQL 8** — the table is `app_user`.
4. **`ddl-auto` must be `validate`.** Anything else and Flyway is no longer the source of truth.
5. **`EXTERNAL_FUNDING` going negative is correct.** Do not "fix" it.
6. **`@Transactional` self-invocation does nothing.** Calling one `@Transactional` method from
   another method on the same bean bypasses the proxy entirely. This bites everyone once.
7. **Bean Validation on request bodies needs `@Valid`** on the controller parameter. Without it the
   annotations are silently decorative.
8. **A fresh Testcontainers MySQL per test class is slow.** Reuse one container across the suite and
   clean data between tests instead.
