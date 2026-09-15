package io.kessai.wallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.wallet.support.AbstractIntegrationTest;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TopUpIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("a top-up credits the wallet and reports the balance after")
    void credits_the_wallet() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");

        topUp(walletId, 10_000, "JPY")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.type").value("TOP_UP"))
                .andExpect(jsonPath("$.amount.minorUnits").value(10_000))
                .andExpect(jsonPath("$.balanceAfter.minorUnits").value(10_000))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(get("/api/v1/wallets/{walletId}", walletId))
                .andExpect(jsonPath("$.balance.minorUnits").value(10_000));
    }

    @Test
    @DisplayName("writes exactly two entries: funding debited, wallet credited, summing to zero")
    void writes_a_balanced_journal() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");

        String body = topUp(walletId, 10_000, "JPY")
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String transactionId = objectMapper.readTree(body).get("transactionId").asString();

        Map<String, Long> amountByAccountType = jdbcTemplate.queryForList("""
                        SELECT a.account_type AS type, j.amount_minor AS amount
                        FROM journal_entry j JOIN account a ON a.id = j.account_id
                        WHERE j.transaction_id = UNHEX(REPLACE(?, '-', ''))
                        """, transactionId).stream()
                .collect(Collectors.toMap(
                        row -> (String) row.get("type"),
                        row -> ((Number) row.get("amount")).longValue()));

        assertThat(amountByAccountType).containsExactlyInAnyOrderEntriesOf(Map.of(
                "EXTERNAL_FUNDING", -10_000L,
                "USER_BALANCE", 10_000L));

        String description = jdbcTemplate.queryForObject(
                "SELECT description FROM ledger_transaction WHERE id = UNHEX(REPLACE(?, '-', ''))",
                String.class, transactionId);
        assertThat(description).isEqualTo("bank-transfer-abc123");
    }

    @Test
    @DisplayName("retrying the same top-up funds the wallet twice -- the bug Week 3 fixes")
    void is_not_idempotent_yet() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");

        String first = topUp(walletId, 10_000, "JPY").andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String second = topUp(walletId, 10_000, "JPY").andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter.minorUnits").value(20_000))
                .andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(second).get("transactionId").asString())
                .isNotEqualTo(objectMapper.readTree(first).get("transactionId").asString());
    }

    @Test
    @DisplayName("the wrong currency is 422 and writes nothing")
    void currency_mismatch_writes_nothing() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");

        topUp(walletId, 100, "USD")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.code").value("CURRENCY_MISMATCH"));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM journal_entry", Long.class)).isZero();
    }

    @Test
    @DisplayName("a wallet that is not ACTIVE cannot be funded")
    void inactive_wallet_returns_409() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");
        jdbcTemplate.update(
                "UPDATE wallet SET status = 'FROZEN' WHERE id = UNHEX(REPLACE(?, '-', ''))", walletId);

        topUp(walletId, 10_000, "JPY")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_ACTIVE"));
    }

    @Test
    @DisplayName("an unknown wallet is 404")
    void unknown_wallet_returns_404() throws Exception {
        topUp(UUID.randomUUID().toString(), 10_000, "JPY")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    @DisplayName("a USD wallet is funded from the USD funding account")
    void usd_wallet_can_be_funded() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "USD");

        topUp(walletId, 1_500, "USD")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.balanceAfter.minorUnits").value(1_500))
                .andExpect(jsonPath("$.balanceAfter.currency").value("USD"));
    }
}
