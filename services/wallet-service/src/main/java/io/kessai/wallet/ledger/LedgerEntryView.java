package io.kessai.wallet.ledger;

import io.kessai.common.money.Currency;
import io.kessai.common.money.Money;
import java.time.Instant;
import java.util.UUID;

/**
 * A journal entry joined with the type of the transaction it belongs to. Built directly by the
 * query so listing a page costs one round trip rather than one per entry.
 */
public record LedgerEntryView(
        UUID entryId,
        UUID transactionId,
        TransactionType type,
        long amountMinor,
        Currency currency,
        Instant createdAt) {

    public Money amount() {
        return Money.of(amountMinor, currency);
    }
}
