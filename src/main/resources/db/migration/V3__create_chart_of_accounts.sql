CREATE TABLE chart_of_accounts (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    code VARCHAR(32) NOT NULL,
    name VARCHAR(180) NOT NULL,
    parent_id UUID REFERENCES chart_of_accounts (id),
    level INTEGER NOT NULL,
    category VARCHAR(40) NOT NULL,
    nature VARCHAR(20) NOT NULL,
    posting_allowed BOOLEAN NOT NULL DEFAULT false,
    system_key VARCHAR(60),
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT chart_of_accounts_level_check CHECK (level > 0),
    CONSTRAINT chart_of_accounts_nature_check CHECK (nature IN ('DEBIT', 'CREDIT')),
    CONSTRAINT chart_of_accounts_company_code_unique UNIQUE (company_id, code),
    CONSTRAINT chart_of_accounts_company_system_key_unique UNIQUE (company_id, system_key)
);

CREATE INDEX chart_of_accounts_company_active_idx ON chart_of_accounts (company_id, active);
CREATE INDEX chart_of_accounts_company_parent_idx ON chart_of_accounts (company_id, parent_id);
