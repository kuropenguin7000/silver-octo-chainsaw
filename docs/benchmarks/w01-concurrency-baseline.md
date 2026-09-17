# Week 1 — concurrency baseline

`k6 run loadtest/concurrency.js`, 2026-09-17. 20 VUs, 200 iterations per write scenario,
100 JPY per top-up, MySQL 8.4 at the default REPEATABLE READ.

## Result

| Scenario | Created | Failed | Failure rate |
|---|---|---|---|
| All VUs top up **one shared wallet** | 19 | 181 | **90.5%** |
| Every VU tops up **its own wallet** | 20 | 180 | **90.0%** |

Read latency p95: **7.7 ms** (`GET /wallets/:id`).

## What it shows

**Giving every VU its own wallet changed nothing** — 90.0% versus 90.5%. The contention is not the
user's wallet row. Every JPY top-up writes a second entry against `EXTERNAL_FUNDING_JPY`, one row
shared by every user on the platform, and that row is the bottleneck. Separating users does not
separate their writes.

Two distinct failure modes appeared, and the dominant one was not the expected one:

| Exception | Count | Layer |
|---|---|---|
| `CannotAcquireLockException` | 309 | InnoDB row-lock contention on the shared account |
| `ObjectOptimisticLockingFailureException` / `StaleObjectStateException` | 52 | JPA `@Version` check |

The database, not Hibernate, rejected most of the writes.

## What held

No money was lost, which is the only hard gate in the script:

| Check | Result |
|---|---|
| Wallet balance == entries × amount (every wallet, via the API) | pass |
| `EXTERNAL_FUNDING_JPY` stored balance vs sum of its entries | −3900 == −3900 |
| All JPY journal entries sum to zero | 0 across 78 entries |
| Accounts where stored balance ≠ derived balance | 0 |

39 top-ups succeeded, 39 × 100 = 3900, 39 × 2 = 78 entries. Failed requests rolled back whole:
no entry without its balance change, no balance change without its entry.

## Week 2

This is the reproduction to fix. The current design trades throughput for safety — it never
corrupts the ledger, but it rejects 90% of concurrent writes. Options to measure against this
baseline:

- Retry on conflict: hides failures from callers, raises latency, risks retry storms.
- Atomic `balance_minor = balance_minor + ?`: no read-modify-write, but gives up the version check.
- Don't materialise the system account balance at all: the hot row disappears; the balance becomes
  a query over entries.

Re-run after each change and record the numbers here.
