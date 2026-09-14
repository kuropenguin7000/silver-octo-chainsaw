package io.kessai.wallet.wallet;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.wallet.support.AbstractIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

class GetWalletIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("the Location returned when opening a wallet now resolves")
    void follows_location_from_open() throws Exception {
        String location = openJpyWalletForNewUser();

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("JPY"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.balance.minorUnits").value(0))
                .andExpect(jsonPath("$.balance.currency").value("JPY"));
    }

    @Test
    @DisplayName("balance is read from account.balance_minor")
    void reads_the_materialised_balance() throws Exception {
        String location = openJpyWalletForNewUser();
        String walletId = location.substring(location.lastIndexOf('/') + 1);

        // No top-up endpoint exists yet, so set the column directly to prove where the value comes from.
        jdbcTemplate.update(
                "UPDATE account SET balance_minor = 10000 "
                        + "WHERE wallet_id = UNHEX(REPLACE(?, '-', '')) AND account_type = 'USER_BALANCE'",
                walletId);

        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance.minorUnits").value(10_000));
    }

    @Test
    @DisplayName("an unknown wallet is 404")
    void unknown_wallet_returns_404() throws Exception {
        mockMvc.perform(get("/api/v1/wallets/{walletId}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("WALLET_NOT_FOUND"));
    }

    private String openJpyWalletForNewUser() throws Exception {
        String userBody = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Rahman", "email": "rahman@example.com" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String userId = objectMapper.readTree(userBody).get("id").asString();

        return mockMvc.perform(post("/api/v1/users/{userId}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currency": "JPY" }
                                """))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
    }
}
