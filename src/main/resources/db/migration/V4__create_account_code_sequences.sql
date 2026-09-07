CREATE TABLE account_code_sequences (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    main_account_code VARCHAR(12) NOT NULL,
    group_code VARCHAR(12) NOT NULL,
    current_value BIGINT NOT NULL DEFAULT 0,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT account_code_sequences_current_value_check CHECK (current_value >= 0),
    CONSTRAINT account_code_sequences_company_group_unique UNIQUE (company_id, main_account_code, group_code)
);

CREATE INDEX account_code_sequences_company_idx ON account_code_sequences (company_id);
