ALTER TABLE vouchers
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN request_fingerprint VARCHAR(64),
    ADD CONSTRAINT vouchers_company_idempotency_unique UNIQUE (company_id, idempotency_key),
    ADD CONSTRAINT vouchers_idempotency_metadata_check CHECK (
        (
            idempotency_key IS NULL
            AND request_fingerprint IS NULL
        )
        OR (
            idempotency_key IS NOT NULL
            AND request_fingerprint ~ '^[0-9a-f]{64}$'
            AND voucher_type IN ('COLLECTION', 'PAYMENT')
        )
    );
