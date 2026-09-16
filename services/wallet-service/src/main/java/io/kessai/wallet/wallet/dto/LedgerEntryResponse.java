package io.kessai.wallet.wallet.dto;

import io.kessai.wallet.ledger.LedgerEntryView;
import io.kessai.wallet.ledger.TransactionType;
import io.kessai.wallet.shared.dto.MoneyResponse;
import java.time.Instant;
import java.util.UUID;

public record LedgerEntryResponse(
        UUID entryId,
        UUID transactionId,
        TransactionType type,
        MoneyResponse amount,
        Instant createdAt) {

    public static LedgerEntryResponse from(LedgerEntryView view) {
        return new LedgerEntryResponse(
                view.entryId(),
                view.transactionId(),
                view.type(),
                MoneyResponse.from(view.amount()),
                view.createdAt());
    }
}
