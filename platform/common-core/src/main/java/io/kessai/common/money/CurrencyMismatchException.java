package io.kessai.common.money;

/**
 * Thrown when two amounts in different currencies are combined.
 *
 * <p>There is no sane default here: silently treating 100 JPY and 100 USD as comparable would
 * corrupt a ledger quietly. Adding amounts across currencies requires an explicit conversion with
 * a rate and a timestamp, which is a domain operation, not an arithmetic one.
 */
public class CurrencyMismatchException extends RuntimeException {

    public CurrencyMismatchException(Currency expected, Currency actual) {
        super("Cannot combine " + expected + " with " + actual + " without an explicit conversion");
    }
}
