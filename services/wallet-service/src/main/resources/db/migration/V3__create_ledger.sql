CREATE TABLE ledger_transaction (
                                    id               BINARY(16)   NOT NULL,
                                    transaction_type VARCHAR(32)  NOT NULL,
                                    currency         CHAR(3)      NOT NULL,
                                    description      VARCHAR(255) NULL,
                                    created_at       DATETIME(6)  NOT NULL,
                                    PRIMARY KEY (id)
) ENGINE = InnoDB;

CREATE TABLE journal_entry (
                               id             BINARY(16)  NOT NULL,
                               transaction_id BINARY(16)  NOT NULL,
                               account_id     BINARY(16)  NOT NULL,
                               amount_minor   BIGINT      NOT NULL,
                               currency       CHAR(3)     NOT NULL,
                               created_at     DATETIME(6) NOT NULL,
                               PRIMARY KEY (id),
                               KEY idx_journal_entry_account (account_id, created_at),
                               KEY idx_journal_entry_transaction (transaction_id),
                               CONSTRAINT fk_journal_entry_transaction
                                   FOREIGN KEY (transaction_id) REFERENCES ledger_transaction (id),
                               CONSTRAINT fk_journal_entry_account
                                   FOREIGN KEY (account_id) REFERENCES account (id),
                               CONSTRAINT ck_journal_entry_nonzero CHECK (amount_minor <> 0)
) ENGINE = InnoDB;