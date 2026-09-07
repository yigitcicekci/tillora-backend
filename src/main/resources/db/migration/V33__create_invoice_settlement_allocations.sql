CREATE TABLE invoice_settlement_allocations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    invoice_id UUID NOT NULL,
    voucher_id UUID NOT NULL,
    idempotency_key UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT invoice_settlement_allocations_company_idempotency_unique
        UNIQUE (company_id, idempotency_key),
    CONSTRAINT invoice_settlement_allocations_company_voucher_unique
        UNIQUE (company_id, voucher_id),
    CONSTRAINT invoice_settlement_allocations_invoice_company_fk
        FOREIGN KEY (invoice_id, company_id) REFERENCES invoices (id, company_id),
    CONSTRAINT invoice_settlement_allocations_voucher_company_fk
        FOREIGN KEY (voucher_id, company_id) REFERENCES vouchers (id, company_id),
    CONSTRAINT invoice_settlement_allocations_fingerprint_check
        CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT invoice_settlement_allocations_amount_check CHECK (amount > 0)
);

CREATE INDEX invoice_settlement_allocations_company_invoice_idx
    ON invoice_settlement_allocations (company_id, invoice_id);
