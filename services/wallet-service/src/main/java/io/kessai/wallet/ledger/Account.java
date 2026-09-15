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
@Table(name = "account")
@EntityListeners(AuditingEntityListener.class)
public class Account {

    @Id
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    /**
     * The owning wallet, or null for a system account. Held as a plain id rather than a
     * {@code @ManyToOne}: aggregates reference each other by identity, which keeps the Week 11
     * split mechanical and avoids lazy-loading traps under {@code open-in-view: false}. The
     * database still enforces the foreign key.
     */
    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "wallet_id", updatable = false)
    private UUID walletId;

    @Column(name = "system_key", length = 64, updatable = false)
    private String systemKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, length = 32, updatable = false)
    private AccountType accountType;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "currency", nullable = false, length = 3, updatable = false)
    private Currency currency;

    @Column(name = "balance_minor", nullable = false)
    private long balanceMinor;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AccountStatus status;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected Account() {
    }

    private Account(UUID id, UUID walletId, String systemKey, AccountType accountType,
                    Currency currency, AccountStatus status) {
        this.id = id;
        this.walletId = walletId;
        this.systemKey = systemKey;
        this.accountType = accountType;
        this.currency = currency;
        this.status = status;
        this.balanceMinor = 0L;
    }

    public static Account forWallet(UUID walletId, AccountType accountType, Currency currency) {
        return new Account(Uuid7.generate(), walletId, null, accountType, currency, AccountStatus.ACTIVE);
    }

    public Money balance() {
        return Money.of(balanceMinor, currency);
    }

    /** Package-private: only the ledger may move a balance, and only alongside a journal entry. */
    void apply(Money amount) {
        balanceMinor = balance().plus(amount).minorUnits();
    }

    public UUID getId() {
        return id;
    }

    public UUID getWalletId() {
        return walletId;
    }

    public String getSystemKey() {
        return systemKey;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public Currency getCurrency() {
        return currency;
    }

    public AccountStatus getStatus() {
        return status;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Account account && Objects.equals(id, account.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
