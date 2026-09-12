package io.kessai.wallet.wallet;

import io.kessai.common.money.Currency;
import io.kessai.wallet.shared.id.Uuid7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

@Entity
@Table(name = "wallet")
@EntityListeners(AuditingEntityListener.class)
public class Wallet {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * Plain id, not a {@code @ManyToOne User}. When identity moves to its own service in Week 11
     * this stays a value; an association would have to be unpicked from every query first.
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WalletStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Wallet() {
    }

    private Wallet(UUID id, UUID userId, Currency currency, WalletStatus status) {
        this.id = id;
        this.userId = userId;
        this.currency = currency;
        this.status = status;
    }

    public static Wallet open(UUID userId, Currency currency) {
        return new Wallet(Uuid7.generate(), userId, currency, WalletStatus.ACTIVE);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Currency getCurrency() {
        return currency;
    }

    public WalletStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Wallet wallet && Objects.equals(id, wallet.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
