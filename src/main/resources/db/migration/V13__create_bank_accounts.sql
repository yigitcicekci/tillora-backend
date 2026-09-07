INSERT INTO permissions (id, code)
VALUES
    ('00000000-0000-0000-0000-000000000030', 'BANK_ACCOUNT_READ'),
    ('00000000-0000-0000-0000-000000000031', 'BANK_ACCOUNT_CREATE'),
    ('00000000-0000-0000-0000-000000000032', 'BANK_ACCOUNT_DISABLE')
ON CONFLICT (code) DO NOTHING;

WITH permission_mapping(role_name, permission_code) AS (
    VALUES
        ('ADMIN', 'BANK_ACCOUNT_READ'),
        ('ADMIN', 'BANK_ACCOUNT_CREATE'),
        ('ADMIN', 'BANK_ACCOUNT_DISABLE'),
        ('ACCOUNTING', 'BANK_ACCOUNT_READ'),
        ('ACCOUNTING', 'BANK_ACCOUNT_CREATE'),
        ('ACCOUNTING', 'BANK_ACCOUNT_DISABLE'),
        ('VIEWER', 'BANK_ACCOUNT_READ')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permission_mapping mapping ON mapping.role_name = r.name
JOIN permissions p ON p.code = mapping.permission_code
ON CONFLICT DO NOTHING;

CREATE TABLE bank_accounts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    name VARCHAR(180) NOT NULL,
    branch VARCHAR(160) NOT NULL,
    iban VARCHAR(34) NOT NULL,
    account_number VARCHAR(64) NOT NULL,
    chart_of_account_id UUID NOT NULL,
    account_code VARCHAR(32) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT bank_accounts_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT bank_accounts_company_chart_unique UNIQUE (company_id, chart_of_account_id),
    CONSTRAINT bank_accounts_company_code_unique UNIQUE (company_id, account_code),
    CONSTRAINT bank_accounts_chart_company_code_fk
        FOREIGN KEY (chart_of_account_id, company_id, account_code)
        REFERENCES chart_of_accounts (id, company_id, code),
    CONSTRAINT bank_accounts_name_check CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT bank_accounts_branch_check CHECK (branch = btrim(branch) AND branch <> ''),
    CONSTRAINT bank_accounts_iban_check CHECK (
        iban ~ '^[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}$'
        AND (iban !~ '^TR' OR iban ~ '^TR[0-9]{24}$')
    ),
    CONSTRAINT bank_accounts_account_number_check CHECK (account_number ~ '^[A-Z0-9]+$'),
    CONSTRAINT bank_accounts_currency_check CHECK (currency ~ '^[A-Z]{3}$')
);

CREATE UNIQUE INDEX bank_accounts_company_iban_unique
    ON bank_accounts (company_id, iban);

CREATE UNIQUE INDEX bank_accounts_company_identity_unique
    ON bank_accounts (company_id, lower(name), lower(branch), account_number);

CREATE INDEX bank_accounts_company_active_created_idx
    ON bank_accounts (company_id, active, created_at DESC);

INSERT INTO account_code_sequences (id, company_id, main_account_code, group_code, current_value, version)
SELECT gen_random_uuid(), account.company_id, account.code, 'DIRECT', 0, 0
FROM chart_of_accounts account
WHERE account.system_key = 'BANKS'
ON CONFLICT (company_id, main_account_code, group_code) DO NOTHING;
