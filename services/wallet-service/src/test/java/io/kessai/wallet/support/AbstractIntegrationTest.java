package io.kessai.wallet.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.kessai.wallet.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.ObjectMapper;

/**
 * Subclasses share one context and therefore one container. Adding a {@code @MockitoBean} or a
 * property override to a subclass forks the cache key and starts a second MySQL.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    /**
     * One cleanup for every table, children first. System accounts are Flyway seed data: their
     * rows stay, but their balances are reset or the ledger invariant would see earlier tests.
     */
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM journal_entry");
        jdbcTemplate.update("DELETE FROM ledger_transaction");
        jdbcTemplate.update("DELETE FROM account WHERE wallet_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM wallet");
        jdbcTemplate.update("DELETE FROM app_user");
        jdbcTemplate.update("UPDATE account SET balance_minor = 0 WHERE wallet_id IS NULL");
    }

    protected String createUser(String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Test User", "email": "%s" }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asString();
    }

    protected String openWallet(String userId, String currency) throws Exception {
        String body = mockMvc.perform(post("/api/v1/users/{userId}/wallets", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "currency": "%s" }
                                """.formatted(currency)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asString();
    }

    protected ResultActions topUp(String walletId, long minorUnits, String currency) throws Exception {
        return mockMvc.perform(post("/api/v1/wallets/{walletId}/topups", walletId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        { "amount": { "minorUnits": %d, "currency": "%s" }, "reference": "bank-transfer-abc123" }
                        """.formatted(minorUnits, currency)));
    }
}
