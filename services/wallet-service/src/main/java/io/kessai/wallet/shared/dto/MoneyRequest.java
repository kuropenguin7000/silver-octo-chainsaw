package io.kessai.wallet.shared.dto;

import io.kessai.common.money.Currency;
import io.kessai.common.money.Money;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record MoneyRequest(

        @NotNull
        @Positive
        Long minorUnits,

        @NotNull
        Currency currency) {

    public Money toMoney() {
        return Money.of(minorUnits, currency);
    }
}
