package io.kessai.wallet.support;

import io.kessai.wallet.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
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
     * One cleanup for every table, children first. Per-class cleanup made each test class depend
     * on which classes ran before it. System accounts (wallet_id IS NULL) are Flyway seed data.
     */
    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM journal_entry");
        jdbcTemplate.update("DELETE FROM ledger_transaction");
        jdbcTemplate.update("DELETE FROM account WHERE wallet_id IS NOT NULL");
        jdbcTemplate.update("DELETE FROM wallet");
        jdbcTemplate.update("DELETE FROM app_user");
    }
}
