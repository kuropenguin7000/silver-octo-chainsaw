package io.kessai.wallet.wallet.dto;

import io.kessai.wallet.ledger.LedgerPosting;
import io.kessai.wallet.ledger.TransactionType;
import io.kessai.wallet.shared.dto.MoneyResponse;
import java.time.Instant;
import java.util.UUID;

public record TopUpResponse(
        UUID transactionId,
        UUID walletId,
        TransactionType type,
        MoneyResponse amount,
        MoneyResponse balanceAfter,
        Instant createdAt) {

    public static TopUpResponse from(UUID walletId, LedgerPosting posting) {
        return new TopUpResponse(
                posting.transactionId(),
                walletId,
                posting.type(),
                MoneyResponse.from(posting.amount()),
                MoneyResponse.from(posting.balanceAfter()),
                posting.postedAt());
    }
}
