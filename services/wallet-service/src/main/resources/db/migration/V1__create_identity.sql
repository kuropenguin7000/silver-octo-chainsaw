CREATE TABLE app_user (
                          id           BINARY(16)   NOT NULL,
                          display_name VARCHAR(100) NOT NULL,
                          email        VARCHAR(255) NOT NULL,
                          status       VARCHAR(20)  NOT NULL,
                          created_at   DATETIME(6)  NOT NULL,
                          updated_at   DATETIME(6)  NOT NULL,
                          version      BIGINT       NOT NULL DEFAULT 0,
                          PRIMARY KEY (id),
                          UNIQUE KEY uq_app_user_email (email)
) ENGINE = InnoDB;