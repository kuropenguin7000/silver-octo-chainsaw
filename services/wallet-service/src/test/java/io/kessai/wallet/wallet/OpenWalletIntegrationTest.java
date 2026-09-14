package io.kessai.wallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.common.money.Currency;
import io.kessai.wallet.ledger.Account;
import io.kessai.wallet.ledger.AccountRepository;
import io.kessai.wallet.ledger.AccountType;
import io.kessai.wallet.support.AbstractIntegrationTest;
import io.kessai.wallet.user.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

class OpenWalletIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private AccountRepository accountRepository;

    private UUID userId;

    @BeforeEach
    void createUser() throws Exception {
        String body = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Rahman", "email": "rahman@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        userId = UUID.fromString(objectMapper.readTree(body).get("id").asString());
    }

    @Test
    @DisplayName("opening a wallet also creates its USER_BALANCE account, in the same transaction")
    void creates_wallet_and_its_balance_account() throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/{userId}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currency": "JPY" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balance.minorUnits").value(0))
                .andExpect(jsonPath("$.balance.currency").value("JPY"))
                .andReturn().getResponse().getContentAsString();

        UUID walletId = UUID.fromString(objectMapper.readTree(body).get("id").asString());

        Wallet wallet = walletRepository.findById(walletId).orElseThrow();
        assertThat(wallet.getUserId()).isEqualTo(userId);
        assertThat(wallet.getCurrency()).isEqualTo(Currency.JPY);
        assertThat(wallet.getStatus()).isEqualTo(WalletStatus.ACTIVE);

        Account balanceAccount = accountRepository
                .findByWalletIdAndAccountType(walletId, AccountType.USER_BALANCE)
                .orElseThrow(() -> new AssertionError("wallet has no USER_BALANCE account"));
        assertThat(balanceAccount.getCurrency()).isEqualTo(Currency.JPY);
        assertThat(balanceAccount.balance().isZero()).isTrue();
        assertThat(balanceAccount.getSystemKey()).isNull();
    }

    @Test
    @DisplayName("a second wallet in the same currency is rejected and leaves no orphan account")
    void rejects_duplicate_currency() throws Exception {
        String body = """
                { "currency": "JPY" }
                """;

        mockMvc.perform(post("/api/v1/users/{userId}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/{userId}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_ALREADY_EXISTS"));

        assertThat(walletRepository.count()).isEqualTo(1);
        assertThat(accountRepository.findAll().stream()
                .filter(a -> a.getWalletId() != null))
                .hasSize(1);
    }

    @Test
    @DisplayName("a different currency is a different wallet")
    void allows_a_second_currency() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currency": "JPY" }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/users/{userId}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currency": "USD" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"));

        assertThat(walletRepository.count()).isEqualTo(2);
    }

    @Test
    @DisplayName("an unknown user is 404 and writes nothing")
    void unknown_user_writes_nothing() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/wallets", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currency": "JPY" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("USER_NOT_FOUND"));

        assertThat(walletRepository.count()).isZero();
    }

    @Test
    @DisplayName("the seeded EXTERNAL_FUNDING system account is untouched by wallet creation")
    void system_account_survives() throws Exception {
        mockMvc.perform(post("/api/v1/users/{userId}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currency": "JPY" }
                                """))
                .andExpect(status().isCreated());

        assertThat(accountRepository.findAll())
                .anySatisfy(account -> {
                    assertThat(account.getSystemKey()).isEqualTo("EXTERNAL_FUNDING_JPY");
                    assertThat(account.getAccountType()).isEqualTo(AccountType.EXTERNAL_FUNDING);
                    assertThat(account.getWalletId()).isNull();
                });
    }
}
