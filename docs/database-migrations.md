# Database migrations — the working rules

Flyway owns the schema. `spring.jpa.hibernate.ddl-auto: validate` means Hibernate checks the
schema and never writes it, so a migration is the only way a change reaches the database.

Migrations live in `services/wallet-service/src/main/resources/db/migration/`.

## What gets a migration

| Change | Migration? |
|---|---|
| New table, column, index, constraint | **Yes** |
| Dropping or renaming anything | **Yes** |
| System/reference rows the app cannot run without (e.g. the `EXTERNAL_FUNDING_JPY` account) | **Yes** — part of the schema contract |
| Lookup/config tables (currencies, fee tiers) | **Yes** |
| Test fixtures | **No** — test factories, or `@Sql` in the test source set |
| Data you are poking at while developing | **No** — call the API, or throw the database away |
| Real business data (a user, a wallet, a top-up) | **Never** — that is what the API is for |

## The unbreakable rule

**Never edit a migration that has already run.**

Flyway stores a checksum of every applied file in `flyway_schema_history`. Change an applied file
and the next boot fails validation. There is no `undo` in Flyway Community, so the only direction
is forward: write a new `V<n>__...` that alters what the earlier one created.

### The exception you will actually use daily

While a migration exists **only on your machine and has not been pushed**, editing it is fine.
Wipe the volume and let every migration replay from scratch:

```bash
docker compose -f deploy/compose/docker-compose.yml down -v
docker compose -f deploy/compose/docker-compose.yml up -d
```

The line is simple: **unpushed = editable, pushed = immutable.**

## One logical change per file

MySQL has no transactional DDL across statements. If a migration runs three `ALTER`s and the
second fails, the first has already committed; Flyway marks the migration failed and you are in a
half-applied state needing `flyway repair`. PostgreSQL would have rolled the whole thing back.

So keep each migration to one coherent change. That is why Week 1 has four files rather than one.

## Naming

`V<version>__<snake_case_description>.sql` — note the **double** underscore.

Sequential integers (`V1`, `V2`, `V3`) while this is a solo project: the folder then reads as the
story of the system. On a team, switch to timestamps (`V20260907143000__...`) so two people
branching at once do not both claim `V7`.

`R__<description>.sql` is a *repeatable* migration: it re-runs whenever its checksum changes,
after all versioned ones. Good for views and stored procedures. Not used yet.

## Forward pointer: Week 15 needs backward-compatible migrations

Week 15 does rolling updates with zero dropped requests. During a rollout, old and new application
code run **against the same database at the same time**. A migration that adds a `NOT NULL` column
with no default breaks every still-running old instance instantly.

The pattern is expand–contract, spread over separate deployments:

1. **Expand** — add the column as nullable. Old code ignores it; new code writes it.
2. **Backfill** — populate existing rows.
3. **Contract** — only once no old code is running, add the `NOT NULL` constraint.

Same shape for renames: add the new column, write to both, migrate readers, then drop the old one.
Never rename in place.

"How do you change a schema with no downtime?" is a standard senior interview question, and this
is the answer.
