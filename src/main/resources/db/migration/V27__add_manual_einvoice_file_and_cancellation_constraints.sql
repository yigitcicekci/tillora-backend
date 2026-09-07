ALTER TABLE electronic_documents
    DROP CONSTRAINT electronic_documents_manual_metadata_check,
    DROP CONSTRAINT electronic_documents_cancellation_reason_check;

ALTER TABLE electronic_documents
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
            AND external_id IS NULL
            AND error_code IS NULL
            AND error_message IS NULL
            AND html_object_id IS NULL
            AND signed_at IS NULL
            AND (
                status NOT IN ('READY_FOR_MANUAL_ISSUE', 'ISSUED')
                OR (
                    xml_object_id IS NULL
                    AND pdf_object_id IS NULL
                    AND zip_object_id IS NULL
                )
            )
            AND (
                status <> 'FILES_UPLOADED'
                OR xml_object_id IS NOT NULL
                OR pdf_object_id IS NOT NULL
                OR zip_object_id IS NOT NULL
            )
        )
    ),
    ADD CONSTRAINT electronic_documents_cancellation_reason_check CHECK (
        (
            status = 'CANCELLED_EXTERNALLY'
            AND cancellation_reason IS NOT NULL
            AND cancellation_reason = btrim(cancellation_reason)
            AND length(cancellation_reason) > 0
        )
        OR (status <> 'CANCELLED_EXTERNALLY' AND cancellation_reason IS NULL)
    );

ALTER TABLE stored_objects
    ADD CONSTRAINT stored_objects_einvoice_key_check CHECK (
        object_key NOT LIKE 'companies/' || company_id::text || '/einvoice/%'
        OR object_key ~ (
            '^companies/' || company_id::text
            || '/einvoice/[0-9]{4}/(0[1-9]|1[0-2])/'
            || '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/'
            || '[0-9a-f]{64}/invoice\.(xml|pdf|zip)$'
        )
    ),
    ADD CONSTRAINT stored_objects_einvoice_content_type_check CHECK (
        object_key NOT LIKE 'companies/' || company_id::text || '/einvoice/%'
        OR (object_key LIKE '%/invoice.xml' AND content_type = 'application/xml')
        OR (object_key LIKE '%/invoice.pdf' AND content_type = 'application/pdf')
        OR (object_key LIKE '%/invoice.zip' AND content_type = 'application/zip')
    );
