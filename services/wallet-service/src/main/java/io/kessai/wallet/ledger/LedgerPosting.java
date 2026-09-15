package io.kessai.wallet.ledger;

import io.kessai.common.money.Money;
import java.time.Instant;
import java.util.UUID;

public record LedgerPosting(
        UUID transactionId,
        TransactionType type,
        Money amount,
        Money balanceAfter,
        Instant postedAt) {
}
