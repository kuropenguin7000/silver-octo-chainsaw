CREATE TABLE wallet (
                        id         BINARY(16)  NOT NULL,
                        user_id    BINARY(16)  NOT NULL,
                        currency   CHAR(3)     NOT NULL,
                        status     VARCHAR(20) NOT NULL,
                        created_at DATETIME(6) NOT NULL,
                        updated_at DATETIME(6) NOT NULL,
                        version    BIGINT      NOT NULL DEFAULT 0,
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_wallet_user_currency (user_id, currency),
                        CONSTRAINT fk_wallet_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) ENGINE = InnoDB;

CREATE TABLE account (
                         id            BINARY(16)  NOT NULL,
                         wallet_id     BINARY(16)  NULL,
                         system_key    VARCHAR(64) NULL,
                         account_type  VARCHAR(32) NOT NULL,
                         currency      CHAR(3)     NOT NULL,
                         balance_minor BIGINT      NOT NULL DEFAULT 0,
                         status        VARCHAR(20) NOT NULL,
                         created_at    DATETIME(6) NOT NULL,
                         updated_at    DATETIME(6) NOT NULL,
                         version       BIGINT      NOT NULL DEFAULT 0,
                         PRIMARY KEY (id),
                         UNIQUE KEY uq_account_wallet_type (wallet_id, account_type),
                         UNIQUE KEY uq_account_system_key (system_key),
                         CONSTRAINT fk_account_wallet FOREIGN KEY (wallet_id) REFERENCES wallet (id),
                         CONSTRAINT ck_account_owner CHECK (
                             (wallet_id IS NOT NULL AND system_key IS NULL) OR
                             (wallet_id IS NULL AND system_key IS NOT NULL)
                             )
) ENGINE = InnoDB;