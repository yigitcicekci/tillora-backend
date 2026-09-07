ALTER TABLE email_deliveries
    ADD COLUMN reference_type VARCHAR(32),
    ADD COLUMN reference_id UUID;

UPDATE email_deliveries
SET reference_type = 'INVOICE',
    reference_id = invoice_id;

ALTER TABLE email_deliveries
    ALTER COLUMN reference_type SET NOT NULL,
    ALTER COLUMN reference_id SET NOT NULL,
    ALTER COLUMN invoice_id DROP NOT NULL,
    ADD CONSTRAINT email_deliveries_reference_type_check CHECK (
        reference_type IN ('INVOICE', 'CURRENT_ACCOUNT_STATEMENT')
    ),
    ADD CONSTRAINT email_deliveries_reference_check CHECK (
        (
            reference_type = 'INVOICE'
            AND invoice_id IS NOT NULL
            AND reference_id = invoice_id
        )
        OR (
            reference_type = 'CURRENT_ACCOUNT_STATEMENT'
            AND invoice_id IS NULL
        )
    );

CREATE UNIQUE INDEX email_deliveries_reference_idempotency_unique
    ON email_deliveries (company_id, reference_type, reference_id, idempotency_key);

CREATE INDEX email_deliveries_reference_created_idx
    ON email_deliveries (company_id, reference_type, reference_id, created_at DESC);
