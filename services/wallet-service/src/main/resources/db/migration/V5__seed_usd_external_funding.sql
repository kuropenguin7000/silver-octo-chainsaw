INSERT INTO account
(id, wallet_id, system_key, account_type, currency,
 balance_minor, status, created_at, updated_at, version)
VALUES
    (UNHEX(REPLACE('00000000-0000-7000-8000-000000000002', '-', '')),
     NULL, 'EXTERNAL_FUNDING_USD', 'EXTERNAL_FUNDING', 'USD',
     0, 'ACTIVE', NOW(6), NOW(6), 0);
