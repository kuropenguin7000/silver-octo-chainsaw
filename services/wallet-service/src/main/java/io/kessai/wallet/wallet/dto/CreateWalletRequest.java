package io.kessai.wallet.wallet.dto;

import io.kessai.common.money.Currency;
import jakarta.validation.constraints.NotNull;

public record CreateWalletRequest(@NotNull Currency currency) {
}
