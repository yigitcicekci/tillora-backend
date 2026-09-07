CREATE TABLE current_account_movements (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    current_account_id UUID NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    reference_type VARCHAR(24) NOT NULL,
    reference_id UUID NOT NULL,
    movement_date DATE NOT NULL,
    due_date DATE,
    debit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT current_account_movements_current_account_company_fk
        FOREIGN KEY (current_account_id, company_id)
        REFERENCES current_accounts (id, company_id),
    CONSTRAINT current_account_movements_reference_unique
        UNIQUE (company_id, current_account_id, reference_type, reference_id),
    CONSTRAINT current_account_movements_type_check CHECK (
        movement_type IN ('SALES_INVOICE', 'PURCHASE_INVOICE', 'COLLECTION', 'PAYMENT')
    ),
    CONSTRAINT current_account_movements_reference_type_check CHECK (
        reference_type IN ('INVOICE', 'VOUCHER')
    ),
    CONSTRAINT current_account_movements_side_check CHECK (
        (debit > 0 AND credit = 0)
        OR (credit > 0 AND debit = 0)
    ),
    CONSTRAINT current_account_movements_due_date_check CHECK (
        due_date IS NULL OR due_date >= movement_date
    )
);

CREATE INDEX current_account_movements_company_account_date_idx
    ON current_account_movements (company_id, current_account_id, movement_date DESC, created_at DESC);

CREATE INDEX current_account_movements_company_due_date_idx
    ON current_account_movements (company_id, due_date)
    WHERE due_date IS NOT NULL;

CREATE FUNCTION prevent_current_account_movement_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION USING
        MESSAGE = 'Current account movements are immutable',
        ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER current_account_movements_immutable
BEFORE UPDATE OR DELETE ON current_account_movements
FOR EACH ROW
EXECUTE FUNCTION prevent_current_account_movement_mutation();
