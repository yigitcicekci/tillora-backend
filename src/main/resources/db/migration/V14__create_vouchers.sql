CREATE TABLE voucher_number_sequences (
    company_id UUID NOT NULL REFERENCES companies (id),
    voucher_type VARCHAR(20) NOT NULL,
    voucher_year SMALLINT NOT NULL,
    current_value INTEGER NOT NULL DEFAULT 0,
    PRIMARY KEY (company_id, voucher_type, voucher_year),
    CONSTRAINT voucher_number_sequences_type_check
        CHECK (voucher_type IN ('COLLECTION', 'PAYMENT', 'OFFSET')),
    CONSTRAINT voucher_number_sequences_year_check CHECK (voucher_year BETWEEN 2000 AND 9999),
    CONSTRAINT voucher_number_sequences_value_check CHECK (current_value BETWEEN 0 AND 999999)
);

CREATE TABLE vouchers (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    voucher_number VARCHAR(24) NOT NULL,
    voucher_type VARCHAR(20) NOT NULL,
    voucher_date DATE NOT NULL,
    movement_note VARCHAR(500),
    document_number VARCHAR(80),
    currency VARCHAR(3) NOT NULL,
    exchange_rate NUMERIC(20, 8) NOT NULL DEFAULT 1,
    status VARCHAR(20) NOT NULL DEFAULT 'DRAFT',
    created_by UUID NOT NULL,
    approved_by UUID,
    cancelled_by UUID,
    cancellation_reason VARCHAR(500),
    total_debit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    total_credit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    approved_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT vouchers_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT vouchers_company_number_unique UNIQUE (company_id, voucher_number),
    CONSTRAINT vouchers_created_user_company_fk
        FOREIGN KEY (created_by, company_id) REFERENCES users (id, company_id),
    CONSTRAINT vouchers_approved_user_company_fk
        FOREIGN KEY (approved_by, company_id) REFERENCES users (id, company_id),
    CONSTRAINT vouchers_cancelled_user_company_fk
        FOREIGN KEY (cancelled_by, company_id) REFERENCES users (id, company_id),
    CONSTRAINT vouchers_type_check CHECK (voucher_type IN ('COLLECTION', 'PAYMENT', 'OFFSET')),
    CONSTRAINT vouchers_status_check CHECK (status IN ('DRAFT', 'APPROVED', 'CANCELLED')),
    CONSTRAINT vouchers_number_format_check CHECK (
        voucher_number ~ '^(THS|TDI|MHS)-[0-9]{4}-[0-9]{6}$'
        AND (
            (voucher_type = 'COLLECTION' AND voucher_number LIKE 'THS-%')
            OR (voucher_type = 'PAYMENT' AND voucher_number LIKE 'TDI-%')
            OR (voucher_type = 'OFFSET' AND voucher_number LIKE 'MHS-%')
        )
    ),
    CONSTRAINT vouchers_movement_note_check CHECK (
        movement_note IS NULL OR (movement_note = btrim(movement_note) AND movement_note <> '')
    ),
    CONSTRAINT vouchers_document_number_check CHECK (
        document_number IS NULL OR (document_number = btrim(document_number) AND document_number <> '')
    ),
    CONSTRAINT vouchers_currency_check CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT vouchers_exchange_rate_check CHECK (exchange_rate > 0),
    CONSTRAINT vouchers_totals_check CHECK (total_debit >= 0 AND total_credit >= 0),
    CONSTRAINT vouchers_approval_balance_check CHECK (
        approved_at IS NULL OR (total_debit > 0 AND total_debit = total_credit)
    ),
    CONSTRAINT vouchers_approval_metadata_check CHECK (
        (approved_by IS NULL AND approved_at IS NULL)
        OR (approved_by IS NOT NULL AND approved_at IS NOT NULL)
    ),
    CONSTRAINT vouchers_cancellation_metadata_check CHECK (
        (cancelled_by IS NULL AND cancelled_at IS NULL AND cancellation_reason IS NULL)
        OR (
            cancelled_by IS NOT NULL
            AND cancelled_at IS NOT NULL
            AND cancellation_reason IS NOT NULL
            AND cancellation_reason = btrim(cancellation_reason)
            AND cancellation_reason <> ''
        )
    ),
    CONSTRAINT vouchers_state_metadata_check CHECK (
        (
            status = 'DRAFT'
            AND approved_by IS NULL
            AND approved_at IS NULL
            AND cancelled_by IS NULL
            AND cancelled_at IS NULL
            AND cancellation_reason IS NULL
        )
        OR (
            status = 'APPROVED'
            AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL
            AND cancelled_by IS NULL
            AND cancelled_at IS NULL
            AND cancellation_reason IS NULL
        )
        OR (
            status = 'CANCELLED'
            AND approved_by IS NOT NULL
            AND approved_at IS NOT NULL
            AND cancelled_by IS NOT NULL
            AND cancelled_at IS NOT NULL
            AND cancellation_reason IS NOT NULL
        )
    )
);

CREATE TABLE voucher_lines (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    voucher_id UUID NOT NULL,
    line_number INTEGER NOT NULL,
    chart_of_account_id UUID NOT NULL,
    chart_account_code VARCHAR(32) NOT NULL,
    chart_account_name VARCHAR(180) NOT NULL,
    current_account_id UUID,
    movement_note VARCHAR(500),
    debit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    credit NUMERIC(19, 4) NOT NULL DEFAULT 0,
    quantity NUMERIC(19, 6),
    due_date DATE,
    currency_amount NUMERIC(19, 4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT voucher_lines_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT voucher_lines_company_voucher_line_unique
        UNIQUE (company_id, voucher_id, line_number),
    CONSTRAINT voucher_lines_voucher_company_fk
        FOREIGN KEY (voucher_id, company_id) REFERENCES vouchers (id, company_id),
    CONSTRAINT voucher_lines_chart_company_code_fk
        FOREIGN KEY (chart_of_account_id, company_id, chart_account_code)
        REFERENCES chart_of_accounts (id, company_id, code),
    CONSTRAINT voucher_lines_current_account_company_fk
        FOREIGN KEY (current_account_id, company_id) REFERENCES current_accounts (id, company_id),
    CONSTRAINT voucher_lines_line_number_check CHECK (line_number > 0),
    CONSTRAINT voucher_lines_chart_code_check CHECK (
        chart_account_code = btrim(chart_account_code) AND chart_account_code <> ''
    ),
    CONSTRAINT voucher_lines_chart_name_check CHECK (
        chart_account_name = btrim(chart_account_name) AND chart_account_name <> ''
    ),
    CONSTRAINT voucher_lines_movement_note_check CHECK (
        movement_note IS NULL OR (movement_note = btrim(movement_note) AND movement_note <> '')
    ),
    CONSTRAINT voucher_lines_debit_credit_check CHECK (
        (debit > 0 AND credit = 0)
        OR (credit > 0 AND debit = 0)
    ),
    CONSTRAINT voucher_lines_quantity_check CHECK (quantity IS NULL OR quantity > 0),
    CONSTRAINT voucher_lines_currency_amount_check CHECK (
        currency_amount IS NULL OR currency_amount > 0
    )
);

CREATE INDEX vouchers_company_date_idx
    ON vouchers (company_id, voucher_date DESC, id DESC);

CREATE INDEX vouchers_company_status_date_idx
    ON vouchers (company_id, status, voucher_date DESC, id DESC);

CREATE INDEX vouchers_company_type_date_idx
    ON vouchers (company_id, voucher_type, voucher_date DESC, id DESC);

CREATE INDEX voucher_lines_company_chart_idx
    ON voucher_lines (company_id, chart_of_account_id);

CREATE INDEX voucher_lines_company_current_account_idx
    ON voucher_lines (company_id, current_account_id)
    WHERE current_account_id IS NOT NULL;
