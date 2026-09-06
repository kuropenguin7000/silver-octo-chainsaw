package io.kessai.common.money;

/**
 * The currencies this platform can hold, with the number of decimal places each one has.
 *
 * <p>JPY has a scale of 0: there is no sub-yen unit, so one minor unit is one yen. USD has a
 * scale of 2, so one minor unit is one cent. Keeping the scale here means {@link Money} never
 * has to guess how to render an amount, and never has to divide to do arithmetic.
 */
public enum Currency {

    JPY(0),
    USD(2);

    private final int scale;

    Currency(int scale) {
        this.scale = scale;
    }

    /** Decimal places this currency has, i.e. minor units per major unit is 10^scale. */
    public int scale() {
        return scale;
    }
}
