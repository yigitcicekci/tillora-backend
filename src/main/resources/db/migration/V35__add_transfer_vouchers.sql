ALTER TABLE voucher_number_sequences
    DROP CONSTRAINT voucher_number_sequences_type_check,
    ADD CONSTRAINT voucher_number_sequences_type_check
        CHECK (voucher_type IN ('COLLECTION', 'PAYMENT', 'OFFSET', 'TRANSFER'));

ALTER TABLE vouchers
    DROP CONSTRAINT vouchers_type_check,
    ADD CONSTRAINT vouchers_type_check
        CHECK (voucher_type IN ('COLLECTION', 'PAYMENT', 'OFFSET', 'TRANSFER')),
    DROP CONSTRAINT vouchers_number_format_check,
    ADD CONSTRAINT vouchers_number_format_check CHECK (
        voucher_number ~ '^(THS|TDI|MHS|VRM)-[0-9]{4}-[0-9]{6}$'
        AND (
            (voucher_type = 'COLLECTION' AND voucher_number LIKE 'THS-%')
            OR (voucher_type = 'PAYMENT' AND voucher_number LIKE 'TDI-%')
            OR (voucher_type = 'OFFSET' AND voucher_number LIKE 'MHS-%')
            OR (voucher_type = 'TRANSFER' AND voucher_number LIKE 'VRM-%')
        )
    ),
    DROP CONSTRAINT vouchers_idempotency_metadata_check,
    ADD CONSTRAINT vouchers_idempotency_metadata_check CHECK (
        (
            idempotency_key IS NULL
            AND request_fingerprint IS NULL
        )
        OR (
            idempotency_key IS NOT NULL
            AND request_fingerprint ~ '^[0-9a-f]{64}$'
            AND voucher_type IN ('COLLECTION', 'PAYMENT', 'OFFSET', 'TRANSFER')
            AND source_type IS NULL
        )
    );
