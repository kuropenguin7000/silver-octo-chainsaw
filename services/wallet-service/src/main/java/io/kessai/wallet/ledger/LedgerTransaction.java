package io.kessai.wallet.ledger;

import io.kessai.common.money.Currency;
import io.kessai.wallet.shared.id.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.domain.Persistable;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "ledger_transaction")
@Immutable
@EntityListeners(AuditingEntityListener.class)
public class LedgerTransaction implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type", nullable = false, length = 32, updatable = false)
    private TransactionType type;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Column(name = "description", length = 255, updatable = false)
    private String description;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Append-only tables have no version column, so this does the job {@code @Version} does on
     * {@code User}: tells Spring Data an assigned-id entity is new. Without it every save SELECTs first.
     */
    @Transient
    private boolean isNew = true;

    protected LedgerTransaction() {
    }

    private LedgerTransaction(UUID id, TransactionType type, Currency currency, String description) {
        this.id = id;
        this.type = type;
        this.currency = currency;
        this.description = description;
    }

    public static LedgerTransaction of(TransactionType type, Currency currency, String description) {
        return new LedgerTransaction(Uuid7.generate(), type, currency, description);
    }

    @PostPersist
    @PostLoad
    void markPersisted() {
        isNew = false;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @Override
    public UUID getId() {
        return id;
    }

    public TransactionType getType() {
        return type;
    }

    public Currency getCurrency() {
        return currency;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof LedgerTransaction transaction && Objects.equals(id, transaction.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
