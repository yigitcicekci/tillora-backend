INSERT INTO permissions (id, code)
VALUES
    ('00000000-0000-0000-0000-000000000027', 'CASH_ACCOUNT_READ'),
    ('00000000-0000-0000-0000-000000000028', 'CASH_ACCOUNT_CREATE'),
    ('00000000-0000-0000-0000-000000000029', 'CASH_ACCOUNT_DISABLE')
ON CONFLICT (code) DO NOTHING;

WITH permission_mapping(role_name, permission_code) AS (
    VALUES
        ('ADMIN', 'CASH_ACCOUNT_READ'),
        ('ADMIN', 'CASH_ACCOUNT_CREATE'),
        ('ADMIN', 'CASH_ACCOUNT_DISABLE'),
        ('ACCOUNTING', 'CASH_ACCOUNT_READ'),
        ('ACCOUNTING', 'CASH_ACCOUNT_CREATE'),
        ('ACCOUNTING', 'CASH_ACCOUNT_DISABLE'),
        ('VIEWER', 'CASH_ACCOUNT_READ')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permission_mapping mapping ON mapping.role_name = r.name
JOIN permissions p ON p.code = mapping.permission_code
ON CONFLICT DO NOTHING;

ALTER TABLE chart_of_accounts
    ADD CONSTRAINT chart_of_accounts_id_company_code_unique UNIQUE (id, company_id, code);

CREATE TABLE cash_accounts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    name VARCHAR(180) NOT NULL,
    chart_of_account_id UUID NOT NULL,
    account_code VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT cash_accounts_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT cash_accounts_company_chart_unique UNIQUE (company_id, chart_of_account_id),
    CONSTRAINT cash_accounts_company_code_unique UNIQUE (company_id, account_code),
    CONSTRAINT cash_accounts_chart_company_code_fk
        FOREIGN KEY (chart_of_account_id, company_id, account_code)
        REFERENCES chart_of_accounts (id, company_id, code),
    CONSTRAINT cash_accounts_name_check CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT cash_accounts_currency_check CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX cash_accounts_company_name_unique
    ON cash_accounts (company_id, lower(name));

CREATE INDEX cash_accounts_company_active_created_idx
    ON cash_accounts (company_id, active, created_at DESC);

INSERT INTO account_code_sequences (id, company_id, main_account_code, group_code, current_value, version)
SELECT gen_random_uuid(), account.company_id, account.code, 'DIRECT', 0, 0
FROM chart_of_accounts account
WHERE account.system_key = 'CASH'
ON CONFLICT (company_id, main_account_code, group_code) DO NOTHING;
