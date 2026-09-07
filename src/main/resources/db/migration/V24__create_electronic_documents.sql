ALTER TABLE stored_objects
    ADD CONSTRAINT stored_objects_id_company_unique UNIQUE (id, company_id);

CREATE TABLE electronic_documents (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    invoice_id UUID NOT NULL,
    provider VARCHAR(32) NOT NULL,
    document_type VARCHAR(32) NOT NULL,
    ettn UUID NOT NULL,
    official_number VARCHAR(64),
    external_id VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    xml_object_id UUID,
    html_object_id UUID,
    pdf_object_id UUID,
    zip_object_id UUID,
    signed_at TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT electronic_documents_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT electronic_documents_company_invoice_unique UNIQUE (company_id, invoice_id),
    CONSTRAINT electronic_documents_company_ettn_unique UNIQUE (company_id, ettn),
    CONSTRAINT electronic_documents_invoice_company_fk
        FOREIGN KEY (invoice_id, company_id)
        REFERENCES invoices (id, company_id),
    CONSTRAINT electronic_documents_xml_object_company_fk
        FOREIGN KEY (xml_object_id, company_id)
        REFERENCES stored_objects (id, company_id),
    CONSTRAINT electronic_documents_html_object_company_fk
        FOREIGN KEY (html_object_id, company_id)
        REFERENCES stored_objects (id, company_id),
    CONSTRAINT electronic_documents_pdf_object_company_fk
        FOREIGN KEY (pdf_object_id, company_id)
        REFERENCES stored_objects (id, company_id),
    CONSTRAINT electronic_documents_zip_object_company_fk
        FOREIGN KEY (zip_object_id, company_id)
        REFERENCES stored_objects (id, company_id),
    CONSTRAINT electronic_documents_provider_check
        CHECK (provider IN ('GIB_PORTAL', 'PRIVATE_INTEGRATOR')),
    CONSTRAINT electronic_documents_type_check CHECK (document_type = 'E_ARCHIVE'),
    CONSTRAINT electronic_documents_status_check CHECK (
        status IN (
            'DRAFT',
            'CREATING_ON_GIB',
            'CREATED_ON_GIB',
            'WAITING_FOR_SMS',
            'SIGNING',
            'SIGNED',
            'FAILED',
            'CANCELLED'
        )
    ),
    CONSTRAINT electronic_documents_error_check CHECK (
        (error_code IS NULL AND error_message IS NULL)
        OR (error_code IS NOT NULL AND error_message IS NOT NULL)
    ),
    CONSTRAINT electronic_documents_signed_check CHECK (
        (status = 'SIGNED' AND signed_at IS NOT NULL)
        OR (status <> 'SIGNED' AND signed_at IS NULL)
    ),
    CONSTRAINT electronic_documents_cancelled_check CHECK (
        (status = 'CANCELLED' AND cancelled_at IS NOT NULL)
        OR (status <> 'CANCELLED' AND cancelled_at IS NULL)
    )
);

CREATE INDEX electronic_documents_company_status_created_idx
    ON electronic_documents (company_id, status, created_at DESC);
