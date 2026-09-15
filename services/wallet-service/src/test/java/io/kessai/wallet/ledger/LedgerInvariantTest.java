package io.kessai.wallet.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.wallet.support.AbstractIntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LedgerInvariantTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("after many top-ups across wallets and currencies, the ledger balances exactly")
    void ledger_balances() throws Exception {
        List<String> jpyWallets = List.of(
                openWallet(createUser("a@example.com"), "JPY"),
                openWallet(createUser("b@example.com"), "JPY"),
                openWallet(createUser("c@example.com"), "JPY"));
        String usdWallet = openWallet(createUser("d@example.com"), "USD");

        long[] jpyAmounts = {10_000, 1, 999_999, 250, 42_000};
        long jpyTotal = 0;
        for (int i = 0; i < jpyAmounts.length; i++) {
            topUp(jpyWallets.get(i % jpyWallets.size()), jpyAmounts[i], "JPY")
                    .andExpect(status().isCreated());
            jpyTotal += jpyAmounts[i];
        }
        topUp(usdWallet, 1_999, "USD").andExpect(status().isCreated());
        topUp(usdWallet, 1, "USD").andExpect(status().isCreated());

        assertThat(count("""
                SELECT COUNT(*) FROM (
                    SELECT currency FROM journal_entry GROUP BY currency HAVING SUM(amount_minor) <> 0
                ) unbalanced""")).as("every currency sums to zero").isZero();

        assertThat(count("""
                SELECT COUNT(*) FROM (
                    SELECT transaction_id FROM journal_entry
                    GROUP BY transaction_id HAVING SUM(amount_minor) <> 0
                ) unbalanced""")).as("every transaction sums to zero").isZero();

        assertThat(count("""
                SELECT COUNT(*) FROM account a
                LEFT JOIN (SELECT account_id, SUM(amount_minor) AS total
                           FROM journal_entry GROUP BY account_id) j ON j.account_id = a.id
                WHERE a.balance_minor <> COALESCE(j.total, 0)""")).as("stored balance equals derived balance").isZero();

        Long fundingBalance = jdbcTemplate.queryForObject(
                "SELECT balance_minor FROM account WHERE system_key = 'EXTERNAL_FUNDING_JPY'", Long.class);
        assertThat(fundingBalance).isEqualTo(-jpyTotal);
    }

    private long count(String sql) {
        return jdbcTemplate.queryForObject(sql, Long.class);
    }
}
