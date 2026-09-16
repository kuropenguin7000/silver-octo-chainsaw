package io.kessai.wallet.wallet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.common.money.Currency;
import io.kessai.common.money.Money;
import io.kessai.wallet.ledger.LedgerEntryView;
import io.kessai.wallet.ledger.LedgerPosting;
import io.kessai.wallet.ledger.TransactionType;
import io.kessai.wallet.shared.error.DomainException;
import io.kessai.wallet.shared.error.ErrorCode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
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

    @Nested
    @DisplayName("GET /api/v1/wallets/{walletId}")
    class GetById {

        @Test
        void returns_200_with_the_balance() throws Exception {
            Wallet wallet = Wallet.open(USER_ID, Currency.JPY);
            given(walletService.getById(wallet.getId()))
                    .willReturn(new WalletWithBalance(wallet, Money.yen(10_000)));

            mockMvc.perform(get("/api/v1/wallets/{walletId}", wallet.getId()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value(wallet.getId().toString()))
                    .andExpect(jsonPath("$.balance.minorUnits").value(10_000))
                    .andExpect(jsonPath("$.balance.currency").value("JPY"));
        }

        @Test
        void unknown_wallet_returns_404() throws Exception {
            UUID missing = UUID.randomUUID();
            willThrow(new DomainException(ErrorCode.WALLET_NOT_FOUND, "No wallet with id " + missing))
                    .given(walletService).getById(missing);

            mockMvc.perform(get("/api/v1/wallets/{walletId}", missing))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
        }

        @Test
        void non_uuid_id_returns_400() throws Exception {
            mockMvc.perform(get("/api/v1/wallets/{walletId}", "abc"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/wallets/{walletId}/entries")
    class Entries {

        private final UUID walletId = UUID.fromString("01927f3b-1d55-7a02-8e77-9f0011223344");

        @Test
        void returns_the_page_with_balance_and_metadata() throws Exception {
            LedgerEntryView entry = new LedgerEntryView(
                    UUID.randomUUID(), UUID.randomUUID(), TransactionType.TOP_UP,
                    10_000, Currency.JPY, Instant.parse("2026-09-16T09:00:00Z"));
            given(walletService.entries(any(), any())).willReturn(new WalletStatement(
                    walletId, Money.yen(10_000),
                    new PageImpl<>(List.of(entry), PageRequest.of(0, 20), 1)));

            mockMvc.perform(get("/api/v1/wallets/{walletId}/entries", walletId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                    .andExpect(jsonPath("$.balance.minorUnits").value(10_000))
                    .andExpect(jsonPath("$.page").value(0))
                    .andExpect(jsonPath("$.size").value(20))
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.totalPages").value(1))
                    .andExpect(jsonPath("$.entries[0].type").value("TOP_UP"))
                    .andExpect(jsonPath("$.entries[0].amount.minorUnits").value(10_000));
        }

        @Test
        void rejects_a_page_size_over_the_cap() throws Exception {
            mockMvc.perform(get("/api/v1/wallets/{walletId}/entries?size=101", walletId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("size"));
        }

        @Test
        void rejects_a_negative_page() throws Exception {
            mockMvc.perform(get("/api/v1/wallets/{walletId}/entries?page=-1", walletId))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("page"));
        }
    }

    @Nested
    @DisplayName("POST /api/v1/wallets/{walletId}/topups")
    class TopUp {

        private final UUID walletId = UUID.fromString("01927f3b-1d55-7a02-8e77-9f0011223344");

        @Test
        void returns_201_with_balance_after() throws Exception {
            given(walletService.topUp(any(), any(), any())).willReturn(new LedgerPosting(
                    UUID.randomUUID(), TransactionType.TOP_UP,
                    Money.yen(10_000), Money.yen(10_000), Instant.parse("2026-09-15T09:00:00Z")));

            mockMvc.perform(post("/api/v1/wallets/{walletId}/topups", walletId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "amount": { "minorUnits": 10000, "currency": "JPY" }, "reference": "ref-1" }
                                    """))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.walletId").value(walletId.toString()))
                    .andExpect(jsonPath("$.type").value("TOP_UP"))
                    .andExpect(jsonPath("$.amount.minorUnits").value(10_000))
                    .andExpect(jsonPath("$.balanceAfter.minorUnits").value(10_000));
        }

        @Test
        void zero_amount_names_the_nested_field() throws Exception {
            mockMvc.perform(post("/api/v1/wallets/{walletId}/topups", walletId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "amount": { "minorUnits": 0, "currency": "JPY" } }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                    .andExpect(jsonPath("$.errors[0].field").value("amount.minorUnits"));
        }

        @Test
        void missing_amount_returns_400() throws Exception {
            mockMvc.perform(post("/api/v1/wallets/{walletId}/topups", walletId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("amount"));
        }

        @Test
        void unsupported_nested_currency_names_the_full_path() throws Exception {
            mockMvc.perform(post("/api/v1/wallets/{walletId}/topups", walletId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "amount": { "minorUnits": 100, "currency": "GBP" } }
                                    """))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.errors[0].field").value("amount.currency"));
        }

        @Test
        void currency_mismatch_returns_422() throws Exception {
            willThrow(new DomainException(ErrorCode.CURRENCY_MISMATCH, "Wallet holds JPY"))
                    .given(walletService).topUp(any(), any(), any());

            mockMvc.perform(post("/api/v1/wallets/{walletId}/topups", walletId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "amount": { "minorUnits": 100, "currency": "USD" } }
                                    """))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));
        }

        @Test
        void inactive_wallet_returns_409() throws Exception {
            willThrow(new DomainException(ErrorCode.WALLET_NOT_ACTIVE, "Wallet is FROZEN"))
                    .given(walletService).topUp(any(), any(), any());

            mockMvc.perform(post("/api/v1/wallets/{walletId}/topups", walletId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    { "amount": { "minorUnits": 100, "currency": "JPY" } }
                                    """))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
        }
    }
}
