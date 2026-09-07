CREATE TABLE stored_objects (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    bucket VARCHAR(63) NOT NULL,
    object_key VARCHAR(1024) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    size BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    available_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT stored_objects_company_key_unique UNIQUE (company_id, object_key),
    CONSTRAINT stored_objects_bucket_key_unique UNIQUE (bucket, object_key),
    CONSTRAINT stored_objects_bucket_check CHECK (
        bucket = lower(bucket)
        AND bucket ~ '^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$'
    ),
    CONSTRAINT stored_objects_key_check CHECK (
        object_key LIKE 'companies/' || company_id::text || '/%'
        AND object_key !~ '(^|/)\.\.?(/|$)'
    ),
    CONSTRAINT stored_objects_content_type_check CHECK (
        content_type IN (
            'application/xml',
            'text/html',
            'application/pdf',
            'application/zip',
            'text/csv',
            'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
        )
    ),
    CONSTRAINT stored_objects_checksum_check CHECK (checksum ~ '^[0-9a-f]{64}$'),
    CONSTRAINT stored_objects_size_check CHECK (size > 0),
    CONSTRAINT stored_objects_status_check CHECK (status IN ('PENDING', 'AVAILABLE', 'FAILED')),
    CONSTRAINT stored_objects_state_check CHECK (
        (status = 'AVAILABLE' AND available_at IS NOT NULL)
        OR (status IN ('PENDING', 'FAILED') AND available_at IS NULL)
    )
);

CREATE INDEX stored_objects_company_status_created_idx
    ON stored_objects (company_id, status, created_at DESC);
