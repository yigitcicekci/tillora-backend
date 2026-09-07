INSERT INTO permissions (id, code)
VALUES ('00000000-0000-0000-0000-000000000039', 'COMPANY_MANAGE')
ON CONFLICT (code) DO NOTHING;

WITH permission_mapping(role_name, permission_code) AS (
    VALUES ('ADMIN', 'COMPANY_MANAGE')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permission_mapping mapping ON mapping.role_name = role.name
JOIN permissions permission ON permission.code = mapping.permission_code
ON CONFLICT DO NOTHING;

ALTER TABLE current_accounts
    ADD COLUMN district VARCHAR(160),
    ADD COLUMN city VARCHAR(160),
    ADD COLUMN postal_code VARCHAR(16),
    ADD COLUMN country_code VARCHAR(2),
    ADD COLUMN country_name VARCHAR(120);

ALTER TABLE current_accounts
    ADD CONSTRAINT current_accounts_country_pair_check CHECK (
        (country_code IS NULL AND country_name IS NULL)
        OR (
            country_code ~ '^[A-Z]{2}$'
            AND country_name IS NOT NULL
        )
    );

CREATE TABLE company_document_profiles (
    company_id UUID PRIMARY KEY REFERENCES companies (id),
    street_name VARCHAR(500) NOT NULL,
    district VARCHAR(160) NOT NULL,
    city VARCHAR(160) NOT NULL,
    postal_code VARCHAR(16),
    country_code VARCHAR(2) NOT NULL DEFAULT 'TR',
    country_name VARCHAR(120) NOT NULL DEFAULT 'Türkiye',
    website VARCHAR(255),
    mersis_number VARCHAR(32),
    trade_registry_number VARCHAR(64),
    default_scenario VARCHAR(24) NOT NULL DEFAULT 'TEMELFATURA',
    logo_object_id UUID,
    logo_media_type VARCHAR(32),
    updated_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT company_document_profiles_logo_company_fk
        FOREIGN KEY (logo_object_id, company_id)
        REFERENCES stored_objects (id, company_id),
    CONSTRAINT company_document_profiles_user_company_fk
        FOREIGN KEY (updated_by, company_id)
        REFERENCES users (id, company_id),
    CONSTRAINT company_document_profiles_country_code_check
        CHECK (country_code ~ '^[A-Z]{2}$'),
    CONSTRAINT company_document_profiles_scenario_check
        CHECK (default_scenario IN ('TEMELFATURA', 'TICARIFATURA')),
    CONSTRAINT company_document_profiles_logo_check
        CHECK (
            (logo_object_id IS NULL AND logo_media_type IS NULL)
            OR (
                logo_object_id IS NOT NULL
                AND logo_media_type IN ('image/png', 'image/jpeg')
            )
        )
);

CREATE TABLE generated_invoice_documents (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    invoice_id UUID NOT NULL,
    ettn UUID NOT NULL,
    scenario VARCHAR(24) NOT NULL,
    status VARCHAR(24) NOT NULL,
    xml_object_id UUID,
    html_object_id UUID,
    pdf_object_id UUID,
    error_code VARCHAR(64),
    error_message VARCHAR(1000),
    requested_by UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT generated_invoice_documents_company_invoice_unique
        UNIQUE (company_id, invoice_id),
    CONSTRAINT generated_invoice_documents_company_ettn_unique
        UNIQUE (company_id, ettn),
    CONSTRAINT generated_invoice_documents_invoice_company_fk
        FOREIGN KEY (invoice_id, company_id)
        REFERENCES invoices (id, company_id),
    CONSTRAINT generated_invoice_documents_user_company_fk
        FOREIGN KEY (requested_by, company_id)
        REFERENCES users (id, company_id),
    CONSTRAINT generated_invoice_documents_xml_company_fk
        FOREIGN KEY (xml_object_id, company_id)
        REFERENCES stored_objects (id, company_id),
    CONSTRAINT generated_invoice_documents_html_company_fk
        FOREIGN KEY (html_object_id, company_id)
        REFERENCES stored_objects (id, company_id),
    CONSTRAINT generated_invoice_documents_pdf_company_fk
        FOREIGN KEY (pdf_object_id, company_id)
        REFERENCES stored_objects (id, company_id),
    CONSTRAINT generated_invoice_documents_scenario_check
        CHECK (scenario IN ('TEMELFATURA', 'TICARIFATURA')),
    CONSTRAINT generated_invoice_documents_status_check
        CHECK (status IN ('PENDING', 'GENERATING', 'READY', 'FAILED')),
    CONSTRAINT generated_invoice_documents_objects_check
        CHECK (
            (
                status = 'READY'
                AND xml_object_id IS NOT NULL
                AND html_object_id IS NOT NULL
                AND pdf_object_id IS NOT NULL
                AND completed_at IS NOT NULL
                AND error_code IS NULL
                AND error_message IS NULL
            )
            OR (
                status <> 'READY'
                AND completed_at IS NULL
                AND (
                    (status = 'FAILED' AND error_code IS NOT NULL AND error_message IS NOT NULL)
                    OR (status <> 'FAILED' AND error_code IS NULL AND error_message IS NULL)
                )
            )
        )
);

CREATE INDEX generated_invoice_documents_pending_idx
    ON generated_invoice_documents (status, created_at)
    WHERE status IN ('PENDING', 'FAILED');
