CREATE TABLE invoice_number_sequences (
    company_id UUID NOT NULL REFERENCES companies (id),
    invoice_type VARCHAR(20) NOT NULL,
    invoice_year SMALLINT NOT NULL,
    current_value INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (company_id, invoice_type, invoice_year),
    CONSTRAINT invoice_number_sequences_type_check
        CHECK (invoice_type IN ('SALES', 'PURCHASE')),
    CONSTRAINT invoice_number_sequences_year_check CHECK (invoice_year BETWEEN 2000 AND 9999),
    CONSTRAINT invoice_number_sequences_value_check CHECK (current_value BETWEEN 0 AND 999999)
);

ALTER TABLE vouchers
    ADD COLUMN source_type VARCHAR(32),
    ADD COLUMN source_id UUID,
    ADD CONSTRAINT vouchers_company_source_unique UNIQUE (company_id, source_type, source_id),
    ADD CONSTRAINT vouchers_source_metadata_check CHECK (
        (source_type IS NULL AND source_id IS NULL)
        OR (
            source_type IN ('SALES_INVOICE', 'PURCHASE_INVOICE')
            AND source_id IS NOT NULL
        )
    );

CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    invoice_number VARCHAR(24) NOT NULL,
    invoice_type VARCHAR(20) NOT NULL,
    current_account_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    invoice_date DATE NOT NULL,
    due_date DATE,
    currency VARCHAR(3) NOT NULL,
    exchange_rate NUMERIC(20, 8) NOT NULL DEFAULT 1,
    subtotal NUMERIC(19, 4) NOT NULL,
    discount_total NUMERIC(19, 4) NOT NULL,
    tax_total NUMERIC(19, 4) NOT NULL,
    grand_total NUMERIC(19, 4) NOT NULL,
    cost_total NUMERIC(19, 4) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    idempotency_key UUID NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    accounting_voucher_id UUID,
    created_by UUID NOT NULL,
    approved_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT invoices_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT invoices_company_number_unique UNIQUE (company_id, invoice_number),
    CONSTRAINT invoices_company_idempotency_unique UNIQUE (company_id, idempotency_key),
    CONSTRAINT invoices_current_account_company_fk
        FOREIGN KEY (current_account_id, company_id)
        REFERENCES current_accounts (id, company_id),
    CONSTRAINT invoices_warehouse_company_fk
        FOREIGN KEY (warehouse_id, company_id)
        REFERENCES warehouses (id, company_id),
    CONSTRAINT invoices_accounting_voucher_company_fk
        FOREIGN KEY (accounting_voucher_id, company_id)
        REFERENCES vouchers (id, company_id),
    CONSTRAINT invoices_created_user_company_fk
        FOREIGN KEY (created_by, company_id)
        REFERENCES users (id, company_id),
    CONSTRAINT invoices_approved_user_company_fk
        FOREIGN KEY (approved_by, company_id)
        REFERENCES users (id, company_id),
    CONSTRAINT invoices_type_check CHECK (invoice_type IN ('SALES', 'PURCHASE')),
    CONSTRAINT invoices_status_check CHECK (status IN ('DRAFT', 'APPROVED', 'CANCELLED')),
    CONSTRAINT invoices_number_format_check CHECK (
        invoice_number ~ '^(SF|AF)-[0-9]{4}-[0-9]{6}$'
        AND (
            (invoice_type = 'SALES' AND invoice_number LIKE 'SF-%')
            OR (invoice_type = 'PURCHASE' AND invoice_number LIKE 'AF-%')
        )
    ),
    CONSTRAINT invoices_due_date_check CHECK (due_date IS NULL OR due_date >= invoice_date),
    CONSTRAINT invoices_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT invoices_exchange_rate_check CHECK (exchange_rate > 0),
    CONSTRAINT invoices_totals_check CHECK (
        subtotal >= 0
        AND discount_total >= 0
        AND discount_total <= subtotal
        AND tax_total >= 0
        AND grand_total = subtotal - discount_total + tax_total
        AND grand_total > 0
        AND cost_total >= 0
    ),
    CONSTRAINT invoices_fingerprint_check CHECK (request_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT invoices_state_metadata_check CHECK (
        (
            status = 'DRAFT'
            AND approved_by IS NULL
            AND approved_at IS NULL
            AND accounting_voucher_id IS NULL
            AND cancelled_at IS NULL
        )
        OR (
            status = 'APPROVED'
            AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL
            AND accounting_voucher_id IS NOT NULL
            AND cancelled_at IS NULL
        )
        OR (
            status = 'CANCELLED'
            AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL
            AND accounting_voucher_id IS NOT NULL
            AND cancelled_at IS NOT NULL
        )
    )
);

CREATE TABLE invoice_lines (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    invoice_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    product_id UUID NOT NULL,
    product_code VARCHAR(64) NOT NULL,
    description VARCHAR(500) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    unit_price NUMERIC(19, 4) NOT NULL,
    discount_rate NUMERIC(5, 2) NOT NULL,
    discount_amount NUMERIC(19, 4) NOT NULL,
    vat_rate NUMERIC(5, 2) NOT NULL,
    vat_amount NUMERIC(19, 4) NOT NULL,
    line_total NUMERIC(19, 4) NOT NULL,
    unit_cost NUMERIC(19, 4) NOT NULL,
    cost_total NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT invoice_lines_company_invoice_line_unique
        UNIQUE (company_id, invoice_id, line_number),
    CONSTRAINT invoice_lines_invoice_company_fk
        FOREIGN KEY (invoice_id, company_id)
        REFERENCES invoices (id, company_id),
    CONSTRAINT invoice_lines_product_company_fk
        FOREIGN KEY (product_id, company_id)
        REFERENCES products (id, company_id),
    CONSTRAINT invoice_lines_line_number_check CHECK (line_number > 0),
    CONSTRAINT invoice_lines_product_code_check CHECK (
        product_code = btrim(product_code) AND product_code <> ''
    ),
    CONSTRAINT invoice_lines_description_check CHECK (
        description = btrim(description) AND description <> ''
    ),
    CONSTRAINT invoice_lines_quantity_check CHECK (quantity > 0),
    CONSTRAINT invoice_lines_unit_check CHECK (
        unit IN (
            'PIECE',
            'KILOGRAM',
            'GRAM',
            'LITER',
            'MILLILITER',
            'METER',
            'SQUARE_METER',
            'CUBIC_METER',
            'PACKAGE',
            'BOX',
            'PAIR',
            'SET',
            'HOUR',
            'DAY'
        )
    ),
    CONSTRAINT invoice_lines_price_check CHECK (unit_price >= 0),
    CONSTRAINT invoice_lines_discount_check CHECK (
        discount_rate BETWEEN 0 AND 100
        AND discount_amount >= 0
    ),
    CONSTRAINT invoice_lines_vat_check CHECK (
        vat_rate BETWEEN 0 AND 100
        AND vat_amount >= 0
    ),
    CONSTRAINT invoice_lines_total_check CHECK (line_total >= 0),
    CONSTRAINT invoice_lines_cost_check CHECK (unit_cost >= 0 AND cost_total >= 0)
);

CREATE INDEX invoices_company_date_idx
    ON invoices (company_id, invoice_date DESC, id DESC);

CREATE INDEX invoices_company_type_status_date_idx
    ON invoices (company_id, invoice_type, status, invoice_date DESC, id DESC);

CREATE INDEX invoices_company_current_account_date_idx
    ON invoices (company_id, current_account_id, invoice_date DESC);

CREATE INDEX invoice_lines_company_product_idx
    ON invoice_lines (company_id, product_id);

ALTER TABLE stock_operations
    DROP CONSTRAINT stock_operations_type_check,
    ADD CONSTRAINT stock_operations_type_check
        CHECK (operation_type IN ('ADJUSTMENT', 'TRANSFER', 'SALES_INVOICE'));
