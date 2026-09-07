ALTER TABLE electronic_documents
    ADD COLUMN issue_method VARCHAR(32) NOT NULL DEFAULT 'GIB_EARCHIVE_AUTOMATION',
    ADD COLUMN issue_date DATE,
    ADD COLUMN registered_by UUID,
    ADD COLUMN registered_at TIMESTAMPTZ,
    ADD COLUMN cancellation_reason VARCHAR(500),
    ADD COLUMN idempotency_key UUID,
    ADD COLUMN request_fingerprint VARCHAR(64),
    DROP CONSTRAINT electronic_documents_type_check,
    DROP CONSTRAINT electronic_documents_status_check,
    DROP CONSTRAINT electronic_documents_cancelled_check;

ALTER TABLE electronic_documents
    ADD CONSTRAINT electronic_documents_registered_user_company_fk
        FOREIGN KEY (registered_by, company_id)
        REFERENCES users (id, company_id),
    ADD CONSTRAINT electronic_documents_type_check
        CHECK (document_type IN ('E_ARCHIVE', 'E_INVOICE')),
    ADD CONSTRAINT electronic_documents_issue_method_check
        CHECK (issue_method IN (
            'GIB_EARCHIVE_AUTOMATION',
            'GIB_PORTAL_MANUAL',
            'PRIVATE_INTEGRATOR'
        )),
    ADD CONSTRAINT electronic_documents_status_check CHECK (
        status IN (
            'DRAFT',
            'CREATING_ON_GIB',
            'CREATED_ON_GIB',
            'WAITING_FOR_SMS',
            'SIGNING',
            'SIGNED',
            'FAILED',
            'CANCELLED',
            'READY_FOR_MANUAL_ISSUE',
            'ISSUED',
            'FILES_UPLOADED',
            'CANCELLED_EXTERNALLY'
        )
    ),
    ADD CONSTRAINT electronic_documents_manual_metadata_check CHECK (
        (
            document_type = 'E_ARCHIVE'
            AND issue_method = 'GIB_EARCHIVE_AUTOMATION'
            AND status IN (
                'DRAFT',
                'CREATING_ON_GIB',
                'CREATED_ON_GIB',
                'WAITING_FOR_SMS',
                'SIGNING',
                'SIGNED',
                'FAILED',
                'CANCELLED'
            )
            AND issue_date IS NULL
            AND registered_by IS NULL
            AND registered_at IS NULL
            AND idempotency_key IS NULL
            AND request_fingerprint IS NULL
            AND cancellation_reason IS NULL
        )
        OR (
            document_type = 'E_INVOICE'
            AND provider = 'GIB_PORTAL'
            AND issue_method = 'GIB_PORTAL_MANUAL'
            AND status IN (
                'READY_FOR_MANUAL_ISSUE',
                'ISSUED',
                'FILES_UPLOADED',
                'CANCELLED_EXTERNALLY'
            )
            AND official_number IS NOT NULL
            AND issue_date IS NOT NULL
            AND registered_by IS NOT NULL
            AND registered_at IS NOT NULL
            AND idempotency_key IS NOT NULL
            AND request_fingerprint IS NOT NULL
        )
    ),
    ADD CONSTRAINT electronic_documents_official_number_check CHECK (
        document_type <> 'E_INVOICE'
        OR official_number IS NULL
        OR (
            official_number = upper(btrim(official_number))
            AND official_number ~ '^[A-Z0-9./_-]+$'
        )
    ),
    ADD CONSTRAINT electronic_documents_registration_pair_check CHECK (
        (registered_by IS NULL AND registered_at IS NULL)
        OR (registered_by IS NOT NULL AND registered_at IS NOT NULL)
    ),
    ADD CONSTRAINT electronic_documents_request_fingerprint_check CHECK (
        request_fingerprint IS NULL
        OR request_fingerprint ~ '^[0-9a-f]{64}$'
    ),
    ADD CONSTRAINT electronic_documents_cancelled_check CHECK (
        (
            status IN ('CANCELLED', 'CANCELLED_EXTERNALLY')
            AND cancelled_at IS NOT NULL
        )
        OR (
            status NOT IN ('CANCELLED', 'CANCELLED_EXTERNALLY')
            AND cancelled_at IS NULL
        )
    ),
    ADD CONSTRAINT electronic_documents_cancellation_reason_check CHECK (
        (status = 'CANCELLED_EXTERNALLY' AND cancellation_reason IS NOT NULL)
        OR (status <> 'CANCELLED_EXTERNALLY' AND cancellation_reason IS NULL)
    );

CREATE UNIQUE INDEX electronic_documents_company_type_official_unique
    ON electronic_documents (company_id, document_type, official_number)
    WHERE official_number IS NOT NULL;

CREATE UNIQUE INDEX electronic_documents_company_idempotency_unique
    ON electronic_documents (company_id, idempotency_key)
    WHERE idempotency_key IS NOT NULL;
