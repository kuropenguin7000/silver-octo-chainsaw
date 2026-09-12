package io.kessai.wallet.shared.dto;

import io.kessai.common.money.Currency;
import io.kessai.common.money.Money;

/**
 * Wire form of an amount. An explicit DTO rather than serialising {@link Money} directly, so the
 * JSON contract cannot drift when that type gains derived accessors.
 */
public record MoneyResponse(long minorUnits, Currency currency) {

    public static MoneyResponse from(Money money) {
        return new MoneyResponse(money.minorUnits(), money.currency());
    }
}
