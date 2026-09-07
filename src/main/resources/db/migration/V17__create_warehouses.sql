CREATE TABLE warehouses (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    code VARCHAR(64) NOT NULL,
    name VARCHAR(180) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT warehouses_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT warehouses_code_check CHECK (
        code = upper(btrim(code))
        AND code ~ '^[A-Z0-9._-]+$'
    ),
    CONSTRAINT warehouses_name_check CHECK (name = btrim(name) AND name <> '')
);

CREATE UNIQUE INDEX warehouses_company_code_unique
    ON warehouses (company_id, lower(code));

CREATE UNIQUE INDEX warehouses_company_name_unique
    ON warehouses (company_id, lower(name));

CREATE INDEX warehouses_company_active_created_idx
    ON warehouses (company_id, active, created_at DESC);
