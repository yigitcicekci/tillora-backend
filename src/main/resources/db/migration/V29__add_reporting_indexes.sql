CREATE INDEX electronic_documents_company_type_created_idx
    ON electronic_documents (company_id, document_type, created_at DESC);

CREATE INDEX audit_logs_company_action_created_idx
    ON audit_logs (company_id, action, created_at DESC);

CREATE INDEX audit_logs_company_entity_type_created_idx
    ON audit_logs (company_id, entity_type, created_at DESC);
