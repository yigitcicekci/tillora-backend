ALTER TABLE audit_logs
    ADD COLUMN before_data JSONB,
    ADD COLUMN after_data JSONB;

ALTER TABLE audit_logs
    ADD CONSTRAINT audit_logs_before_data_object_check CHECK (
        before_data IS NULL OR jsonb_typeof(before_data) = 'object'
    ),
    ADD CONSTRAINT audit_logs_after_data_object_check CHECK (
        after_data IS NULL OR jsonb_typeof(after_data) = 'object'
    );

CREATE INDEX audit_logs_company_user_created_idx
    ON audit_logs (company_id, user_id, created_at DESC);
