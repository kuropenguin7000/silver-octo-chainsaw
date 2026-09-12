package io.kessai.wallet.wallet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.common.money.Currency;
import io.kessai.common.money.Money;
import io.kessai.wallet.shared.error.DomainException;
import io.kessai.wallet.shared.error.ErrorCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(WalletController.class)
class WalletControllerTest {

    private static final UUID USER_ID = UUID.fromString("01927f3a-8c21-7c4e-9b3d-1a2b3c4d5e6f");

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WalletService walletService;

    @Nested
    @DisplayName("opening a wallet")
    class Open {

        @Test
        void returns_201_with_a_zero_balance() throws Exception {
            Wallet wallet = Wallet.open(USER_ID, Currency.JPY);
            given(walletService.open(any(), any()))
                    .willReturn(new WalletWithBalance(wallet, Money.zero(Currency.JPY)));

            mockMvc.perform(post("/api/v1/users/{userId}/wallets", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "currency": "JPY" }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(header().string("Location", "/api/v1/wallets/" + wallet.getId()))
                    .andExpect(jsonPath("$.userId").value(USER_ID.toString()))
                    .andExpect(jsonPath("$.currency").value("JPY"))
                    .andExpect(jsonPath("$.status").value("ACTIVE"))
                    .andExpect(jsonPath("$.balance.minorUnits").value(0))
                    .andExpect(jsonPath("$.balance.currency").value("JPY"));
        }
    }

    @Nested
    @DisplayName("failures")
    class Failures {

        @Test
        void unknown_user_returns_404() throws Exception {
            willThrow(new DomainException(ErrorCode.USER_NOT_FOUND, "No user with id " + USER_ID))
                    .given(walletService).open(any(), any());

            mockMvc.perform(post("/api/v1/users/{userId}/wallets", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "currency": "JPY" }
                                    """))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));
        }

        @Test
        void duplicate_wallet_returns_409() throws Exception {
            willThrow(new DomainException(
                    ErrorCode.WALLET_ALREADY_EXISTS, "User already has a JPY wallet"))
                    .given(walletService).open(any(), any());

            mockMvc.perform(post("/api/v1/users/{userId}/wallets", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "currency": "JPY" }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("WALLET_ALREADY_EXISTS"))
                    .andExpect(jsonPath("$.title").value("Wallet already exists"));
        }

        @Test
        void unsupported_currency_names_the_accepted_values() throws Exception {
            mockMvc.perform(post("/api/v1/users/{userId}/wallets", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "currency": "GBP" }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("currency"))
                    .andExpect(jsonPath("$.errors[0].message").value("must be one of [JPY, USD]"));
        }

        @Test
        void missing_currency_returns_400() throws Exception {
            mockMvc.perform(post("/api/v1/users/{userId}/wallets", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("currency"));
        }

        @Test
        void unparseable_body_returns_400() throws Exception {
            mockMvc.perform(post("/api/v1/users/{userId}/wallets", USER_ID)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{ not json"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
    }
}
