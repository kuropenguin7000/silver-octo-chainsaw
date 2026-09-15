package io.kessai.wallet.ledger;

import io.kessai.common.money.Currency;
import io.kessai.common.money.Money;
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

/** One side of a movement. Positive means money into the account, negative means out of it. */
@Entity
@Table(name = "journal_entry")
@Immutable
@EntityListeners(AuditingEntityListener.class)
public class JournalEntry implements Persistable<UUID> {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "amount_minor", nullable = false, updatable = false)
    private long amountMinor;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean isNew = true;

    protected JournalEntry() {
    }

    private JournalEntry(UUID id, UUID transactionId, UUID accountId, Money amount) {
        this.id = id;
        this.transactionId = transactionId;
        this.accountId = accountId;
        this.amountMinor = amount.minorUnits();
        this.currency = amount.currency();
    }

    static JournalEntry of(UUID transactionId, UUID accountId, Money amount) {
        if (amount.isZero()) {
            throw new IllegalArgumentException("A journal entry cannot be zero");
        }
        return new JournalEntry(Uuid7.generate(), transactionId, accountId, amount);
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

    public UUID getTransactionId() {
        return transactionId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public Money amount() {
        return Money.of(amountMinor, currency);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof JournalEntry entry && Objects.equals(id, entry.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
