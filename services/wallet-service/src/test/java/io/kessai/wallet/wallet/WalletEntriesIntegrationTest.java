package io.kessai.wallet.wallet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.wallet.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class WalletEntriesIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("entries come back newest first, with the transaction type joined in")
    void lists_newest_first() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");
        topUp(walletId, 100, "JPY").andExpect(status().isCreated());
        topUp(walletId, 200, "JPY").andExpect(status().isCreated());
        topUp(walletId, 300, "JPY").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/wallets/{walletId}/entries", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.walletId").value(walletId))
                .andExpect(jsonPath("$.balance.minorUnits").value(600))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.entries.length()").value(3))
                .andExpect(jsonPath("$.entries[0].amount.minorUnits").value(300))
                .andExpect(jsonPath("$.entries[1].amount.minorUnits").value(200))
                .andExpect(jsonPath("$.entries[2].amount.minorUnits").value(100))
                .andExpect(jsonPath("$.entries[0].type").value("TOP_UP"))
                .andExpect(jsonPath("$.entries[0].entryId").exists())
                .andExpect(jsonPath("$.entries[0].transactionId").exists());
    }

    @Test
    @DisplayName("only the wallet's own side of each transaction is listed, never the funding account")
    void excludes_the_system_account_side() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");
        topUp(walletId, 100, "JPY").andExpect(status().isCreated());

        // The top-up wrote two entries; the user must see only their own, positive one.
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM journal_entry", Long.class))
                .isEqualTo(2);

        mockMvc.perform(get("/api/v1/wallets/{walletId}/entries", walletId))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.entries[0].amount.minorUnits").value(100));
    }

    @Test
    @DisplayName("paging splits the entries without repeating or dropping any")
    void pages_without_overlap() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");
        topUp(walletId, 100, "JPY").andExpect(status().isCreated());
        topUp(walletId, 200, "JPY").andExpect(status().isCreated());
        topUp(walletId, 300, "JPY").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/wallets/{walletId}/entries?page=0&size=2", walletId))
                .andExpect(jsonPath("$.entries.length()").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.entries[0].amount.minorUnits").value(300))
                .andExpect(jsonPath("$.entries[1].amount.minorUnits").value(200));

        mockMvc.perform(get("/api/v1/wallets/{walletId}/entries?page=1&size=2", walletId))
                .andExpect(jsonPath("$.entries.length()").value(1))
                .andExpect(jsonPath("$.entries[0].amount.minorUnits").value(100));
    }

    @Test
    @DisplayName("one wallet never sees another wallet's entries")
    void wallets_are_isolated() throws Exception {
        String mine = openWallet(createUser("a@example.com"), "JPY");
        String theirs = openWallet(createUser("b@example.com"), "JPY");
        topUp(mine, 100, "JPY").andExpect(status().isCreated());
        topUp(theirs, 999, "JPY").andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/wallets/{walletId}/entries", mine))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.entries[0].amount.minorUnits").value(100));
    }

    @Test
    @DisplayName("a wallet with no movements returns an empty page, not a 404")
    void empty_wallet_returns_empty_page() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");

        mockMvc.perform(get("/api/v1/wallets/{walletId}/entries", walletId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.entries.length()").value(0))
                .andExpect(jsonPath("$.balance.minorUnits").value(0));
    }

    @Test
    @DisplayName("an unknown wallet is 404")
    void unknown_wallet_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/{walletId}/entries", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    @Test
    @DisplayName("a page size above the cap is rejected rather than reading the whole ledger")
    void page_size_is_capped() throws Exception {
        String walletId = openWallet(createUser("a@example.com"), "JPY");

        mockMvc.perform(get("/api/v1/wallets/{walletId}/entries?size=101", walletId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));

        mockMvc.perform(get("/api/v1/wallets/{walletId}/entries?page=-1", walletId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("page"));
    }
}
