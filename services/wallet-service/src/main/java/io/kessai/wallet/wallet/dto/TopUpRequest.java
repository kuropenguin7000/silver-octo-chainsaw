package io.kessai.wallet.wallet.dto;

import io.kessai.wallet.shared.dto.MoneyRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record TopUpRequest(

        @NotNull
        @Valid
        MoneyRequest amount,

        @Size(max = 64)
        String reference) {
}
