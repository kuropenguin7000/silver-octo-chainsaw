package io.kessai.wallet.wallet.dto;

import io.kessai.common.money.Currency;
import io.kessai.wallet.shared.dto.MoneyResponse;
import io.kessai.wallet.wallet.Wallet;
import io.kessai.wallet.wallet.WalletStatus;
import io.kessai.wallet.wallet.WalletWithBalance;
import java.time.Instant;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        UUID userId,
        Currency currency,
        WalletStatus status,
        MoneyResponse balance,
        Instant createdAt) {

    public static WalletResponse from(WalletWithBalance walletWithBalance) {
        Wallet wallet = walletWithBalance.wallet();
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getCurrency(),
                wallet.getStatus(),
                MoneyResponse.from(walletWithBalance.balance()),
                wallet.getCreatedAt());
    }
}
