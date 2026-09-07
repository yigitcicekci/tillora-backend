CREATE TABLE current_accounts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    name VARCHAR(180) NOT NULL,
    legal_name VARCHAR(240),
    tax_number VARCHAR(20),
    tax_office VARCHAR(120),
    identity_number VARCHAR(20),
    phone VARCHAR(40),
    email VARCHAR(160),
    address TEXT,
    trade_type VARCHAR(24) NOT NULL,
    relationship_type VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT current_accounts_trade_type_check CHECK (trade_type IN ('RETAIL', 'WHOLESALE')),
    CONSTRAINT current_accounts_relationship_type_check CHECK (relationship_type IN ('CUSTOMER', 'SUPPLIER', 'BOTH')),
    CONSTRAINT current_accounts_status_check CHECK (status IN ('ACTIVE', 'PASSIVE')),
    CONSTRAINT current_accounts_tax_or_identity_check CHECK (tax_number IS NOT NULL OR identity_number IS NOT NULL),
    CONSTRAINT current_accounts_company_tax_number_unique UNIQUE (company_id, tax_number),
    CONSTRAINT current_accounts_company_identity_number_unique UNIQUE (company_id, identity_number)
);

CREATE TABLE current_account_ledger_accounts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    current_account_id UUID NOT NULL REFERENCES current_accounts (id),
    role VARCHAR(24) NOT NULL,
    chart_of_account_id UUID NOT NULL REFERENCES chart_of_accounts (id),
    main_account_code VARCHAR(12) NOT NULL,
    group_code VARCHAR(12) NOT NULL,
    sequence_number BIGINT NOT NULL,
    full_account_code VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT current_account_ledger_accounts_role_check CHECK (role IN ('RECEIVABLE', 'PAYABLE')),
    CONSTRAINT current_account_ledger_accounts_company_role_unique UNIQUE (company_id, current_account_id, role),
    CONSTRAINT current_account_ledger_accounts_company_code_unique UNIQUE (company_id, full_account_code)
);

CREATE INDEX current_accounts_company_status_idx ON current_accounts (company_id, status);
CREATE INDEX current_account_ledger_accounts_current_account_idx ON current_account_ledger_accounts (current_account_id);
