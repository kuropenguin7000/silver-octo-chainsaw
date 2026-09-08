package io.kessai.wallet.shared.jpa;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Deliberately its own class. On {@code WalletServiceApplication} this would load in every
 * {@code @WebMvcTest}, dragging JPA into the web slices and breaking them.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfiguration {
}
