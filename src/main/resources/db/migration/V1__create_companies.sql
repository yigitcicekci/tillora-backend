CREATE TABLE companies (
    id UUID PRIMARY KEY,
    name VARCHAR(160) NOT NULL,
    legal_name VARCHAR(240) NOT NULL,
    tax_number VARCHAR(20) NOT NULL,
    tax_office VARCHAR(120),
    address TEXT,
    phone VARCHAR(40),
    email VARCHAR(160),
    currency CHAR(3) NOT NULL DEFAULT 'TRY',
    timezone VARCHAR(64) NOT NULL DEFAULT 'Europe/Istanbul',
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT companies_status_check CHECK (status IN ('ACTIVE', 'PASSIVE')),
    CONSTRAINT companies_tax_number_unique UNIQUE (tax_number)
);

CREATE INDEX companies_status_idx ON companies (status);
