INSERT INTO permissions (id, code)
VALUES ('00000000-0000-0000-0000-000000000042', 'INVOICE_EMAIL_SEND')
ON CONFLICT (code) DO NOTHING;

WITH permission_mapping(role_name, permission_code) AS (
    VALUES
        ('ADMIN', 'INVOICE_EMAIL_SEND'),
        ('ACCOUNTING', 'INVOICE_EMAIL_SEND')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permission_mapping mapping ON mapping.role_name = role.name
JOIN permissions permission ON permission.code = mapping.permission_code
ON CONFLICT DO NOTHING;

CREATE TABLE email_deliveries (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    invoice_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    provider_message_id VARCHAR(255),
    status VARCHAR(16) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    to_recipients JSONB NOT NULL,
    cc_recipients JSONB NOT NULL,
    attachment_types JSONB NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    failure_code VARCHAR(64),
    failure_message VARCHAR(500),
    created_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    sent_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT email_deliveries_invoice_company_fk
        FOREIGN KEY (invoice_id, company_id)
        REFERENCES invoices (id, company_id),
    CONSTRAINT email_deliveries_user_company_fk
        FOREIGN KEY (created_by, company_id)
        REFERENCES users (id, company_id),
    CONSTRAINT email_deliveries_provider_check CHECK (provider = 'BREVO'),
    CONSTRAINT email_deliveries_status_check CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT email_deliveries_recipients_check CHECK (
        jsonb_typeof(to_recipients) = 'array'
        AND jsonb_array_length(to_recipients) > 0
        AND jsonb_typeof(cc_recipients) = 'array'
    ),
    CONSTRAINT email_deliveries_attachments_check CHECK (
        jsonb_typeof(attachment_types) = 'array'
        AND jsonb_array_length(attachment_types) > 0
    ),
    CONSTRAINT email_deliveries_fingerprint_check CHECK (
        request_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    CONSTRAINT email_deliveries_result_check CHECK (
        (
            status = 'PENDING'
            AND provider_message_id IS NULL
            AND failure_code IS NULL
            AND failure_message IS NULL
            AND sent_at IS NULL
            AND failed_at IS NULL
        )
        OR (
            status = 'SENT'
            AND provider_message_id IS NOT NULL
            AND failure_code IS NULL
            AND failure_message IS NULL
            AND sent_at IS NOT NULL
            AND failed_at IS NULL
        )
        OR (
            status = 'FAILED'
            AND provider_message_id IS NULL
            AND failure_code IS NOT NULL
            AND failure_message IS NOT NULL
            AND sent_at IS NULL
            AND failed_at IS NOT NULL
        )
    ),
    CONSTRAINT email_deliveries_company_invoice_idempotency_unique
        UNIQUE (company_id, invoice_id, idempotency_key)
);

CREATE INDEX email_deliveries_company_invoice_created_idx
    ON email_deliveries (company_id, invoice_id, created_at DESC);

CREATE INDEX email_deliveries_company_created_idx
    ON email_deliveries (company_id, created_at DESC);

CREATE UNIQUE INDEX email_deliveries_provider_message_unique
    ON email_deliveries (provider, provider_message_id)
    WHERE provider_message_id IS NOT NULL;
