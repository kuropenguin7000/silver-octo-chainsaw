package io.kessai.common.money;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * An exact monetary amount, held as a whole number of a currency's minor units.
 *
 * <p><b>Money is never a {@code double}.</b> IEEE-754 binary floating point cannot represent 0.1
 * exactly, so amounts drift as they accumulate. A ledger that drifts is a ledger that does not
 * balance, and an unbalanced ledger is an outage. {@code long} minor units are exact, are cheap to
 * store and index, and make the balance invariant a plain integer sum.
 *
 * <p>Arithmetic uses {@link Math#addExact} rather than {@code +} so that an overflow throws
 * instead of silently wrapping a balance around to a negative number. At JPY scale a {@code long}
 * holds roughly 9.2 quintillion yen, so overflow means a bug upstream, and failing loudly is the
 * only safe response.
 */
public record Money(long minorUnits, Currency currency) implements Comparable<Money> {

    public Money {
        Objects.requireNonNull(currency, "currency");
    }

    public static Money of(long minorUnits, Currency currency) {
        return new Money(minorUnits, currency);
    }

    public static Money yen(long yen) {
        return new Money(yen, Currency.JPY);
    }

    public static Money zero(Currency currency) {
        return new Money(0L, currency);
    }

    public Money plus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.addExact(minorUnits, other.minorUnits), currency);
    }

    public Money minus(Money other) {
        requireSameCurrency(other);
        return new Money(Math.subtractExact(minorUnits, other.minorUnits), currency);
    }

    /** The same amount on the opposite side of the ledger. A debit negated is its credit. */
    public Money negated() {
        return new Money(Math.negateExact(minorUnits), currency);
    }

    public boolean isZero() {
        return minorUnits == 0L;
    }

    public boolean isPositive() {
        return minorUnits > 0L;
    }

    public boolean isNegative() {
        return minorUnits < 0L;
    }

    public boolean isGreaterThanOrEqualTo(Money other) {
        requireSameCurrency(other);
        return minorUnits >= other.minorUnits;
    }

    @Override
    public int compareTo(Money other) {
        requireSameCurrency(other);
        return Long.compare(minorUnits, other.minorUnits);
    }

    /**
     * Human-readable major-unit form, for logs and API responses only.
     *
     * <p>Deliberately returns {@link BigDecimal} and not {@code double}: this is a presentation
     * concern and must not become an arithmetic path back into the domain.
     */
    public BigDecimal toMajorUnits() {
        return BigDecimal.valueOf(minorUnits, currency.scale());
    }

    private void requireSameCurrency(Money other) {
        Objects.requireNonNull(other, "other");
        if (currency != other.currency) {
            throw new CurrencyMismatchException(currency, other.currency);
        }
    }

    @Override
    public String toString() {
        return toMajorUnits().toPlainString() + " " + currency;
    }
}
