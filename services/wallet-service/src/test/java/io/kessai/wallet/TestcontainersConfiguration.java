package io.kessai.wallet;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A bean rather than a JUnit {@code @Container} field, so Spring's context cache reuses one
 * container across every test class instead of starting a fresh MySQL per class.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    @Bean
    @ServiceConnection
    MySQLContainer mysqlContainer() {
        // Pinned to match docker-compose. `mysql:latest` would drift from production silently.
        return new MySQLContainer(DockerImageName.parse("mysql:8.4"));
    }
}
