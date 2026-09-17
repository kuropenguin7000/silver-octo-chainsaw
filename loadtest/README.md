# Load and concurrency tests

## Running

MySQL must be up, and the service must be running on a port that is **not** 8080 — on this
machine 8080 is a Vite dev server that answers 200 with HTML to every path, which would make read
checks pass against nothing. `concurrency.js` refuses to start if the target does not look like
the wallet service.

```bash
docker compose -f deploy/compose/docker-compose.yml up -d
```

```bash
./gradlew :services:wallet-service:bootRun --args='--server.port=8081'
```

```bash
k6 run loadtest/concurrency.js
```

Run it from the repository root, so the JSON summary lands in `docs/benchmarks/`.

Knobs: `-e BASE_URL=http://localhost:8081 -e VUS=20 -e ITERATIONS=200 -e AMOUNT=100 -e CURRENCY=JPY`

## What it measures

| Scenario | Question it answers |
|---|---|
| `hot_wallet` | What happens when many requests top up **one** wallet at once? |
| `distinct_wallets` | Same load, but every VU has its **own** wallet. Does the contention go away? |
| `reads` | Baseline p95 for `GET /wallets/:id` and `/entries` |

The comparison is the whole point. Every JPY top-up writes two journal entries: one to the user's
account, one to `EXTERNAL_FUNDING_JPY`. That system account is a single row shared by every user,
so if `distinct_wallets` fails at a similar rate to `hot_wallet`, the bottleneck is not the user's
wallet — it is that one row.

## Reading the result

**Failed top-ups are not a test failure.** Right now `Account` carries `@Version`, so two
concurrent writes to the same row make one of them fail with an optimistic-lock error, which the
API returns as a 500. That is the finding, not a bug in the test. `http_req_failed` is
deliberately left ungated.

**The gate is that no money was lost.** In teardown, every wallet is checked with its own API:

```
balance.minorUnits  ==  entries.totalElements * AMOUNT
```

Each successful top-up adds one entry and one increment. If a balance update were lost while its
journal entry survived, the balance would fall short and `ledger_consistent` would fail the run.

To check the other side of the ledger — the system account has no API:

```bash
docker exec kessai-mysql mysql -ukessai -pkessai kessai_wallet -e "SELECT system_key, balance_minor, (SELECT COALESCE(SUM(amount_minor),0) FROM journal_entry j WHERE j.account_id = a.id) AS derived FROM account a WHERE a.wallet_id IS NULL"
```

`balance_minor` and `derived` must match, and both must be negative.

## Week 2

This script is the reproduction to fix. Expect the failure rate to change as locking strategy
changes: optimistic locking trades lost updates for failed requests, pessimistic locking trades
them for latency, and an atomic `balance_minor = balance_minor + ?` avoids both but gives up the
version check. Re-run after each change and keep the numbers.
