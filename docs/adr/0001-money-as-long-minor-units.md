# ADR 0001 — Money is a `long` count of minor units, never a floating-point number

- Status: accepted
- Date: 2026-09-06
- Week: 1

## Context

Every amount in this system is money, and the ledger's core invariant is that the signed sum of
all journal entries is exactly zero. Any representation that loses precision breaks that invariant
in a way that is silent, cumulative, and extremely hard to reconcile after the fact.

`double` and `float` are IEEE-754 binary; they cannot represent decimal 0.1 exactly. Errors are
tiny per operation and unbounded in aggregate. `BigDecimal` is exact but carries a scale that can
drift between call sites, allocates on every operation, and compares badly (`equals` distinguishes
`1.0` from `1.00`), which is a subtle source of bugs in `Map` keys and assertions.

## Decision

Money is the record `Money(long minorUnits, Currency currency)`.

- `long` is exact, cheap to store, cheap to index, and lets the balance invariant be a plain
  `SUM()` in SQL.
- `Currency` carries the scale, so JPY (scale 0, one minor unit = one yen) and USD (scale 2)
  render correctly without the domain ever dividing.
- Arithmetic uses `Math.addExact` / `subtractExact` / `negateExact`, so overflow throws rather
  than wrapping a balance to a negative number.
- Mixing currencies throws `CurrencyMismatchException`. Cross-currency addition requires an
  explicit conversion with a rate and a timestamp, which is a domain operation, not arithmetic.
- `toMajorUnits()` returns `BigDecimal` and exists only for rendering. It must never feed back
  into domain arithmetic.

## Consequences

- Good: exact by construction; the invariant test is a trivial integer sum; no allocation in the
  hot path; database columns are `BIGINT`.
- Good: overflow and currency mistakes fail loudly and early, in tests, not in reconciliation.
- Cost: callers must think in minor units. For JPY this is invisible (scale 0), but a USD amount
  of "15.00" is written as `Money.of(1500, USD)`, which reads wrong until you are used to it.
- Cost: a future currency with a scale other than 0 or 2 needs the enum extended, not just a
  config value.
