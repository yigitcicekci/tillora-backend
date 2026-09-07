ALTER TABLE vouchers
    DROP CONSTRAINT vouchers_idempotency_metadata_check,
    ADD CONSTRAINT vouchers_idempotency_metadata_check CHECK (
        (
            idempotency_key IS NULL
            AND request_fingerprint IS NULL
        )
        OR (
            idempotency_key IS NOT NULL
            AND request_fingerprint ~ '^[0-9a-f]{64}$'
            AND voucher_type IN ('COLLECTION', 'PAYMENT', 'OFFSET')
            AND source_type IS NULL
        )
    );
