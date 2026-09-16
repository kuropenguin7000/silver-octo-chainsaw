package io.kessai.wallet.wallet.dto;

import io.kessai.wallet.ledger.LedgerEntryView;
import io.kessai.wallet.shared.dto.MoneyResponse;
import io.kessai.wallet.wallet.WalletStatement;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;

public record WalletEntriesResponse(
        UUID walletId,
        MoneyResponse balance,
        List<LedgerEntryResponse> entries,
        int page,
        int size,
        long totalElements,
        int totalPages) {

    public static WalletEntriesResponse from(WalletStatement statement) {
        Page<LedgerEntryView> entries = statement.entries();
        return new WalletEntriesResponse(
                statement.walletId(),
                MoneyResponse.from(statement.balance()),
                entries.getContent().stream().map(LedgerEntryResponse::from).toList(),
                entries.getNumber(),
                entries.getSize(),
                entries.getTotalElements(),
                entries.getTotalPages());
    }
}
