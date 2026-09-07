CREATE TABLE audit_logs (
    id UUID PRIMARY KEY,
    company_id UUID,
    user_id UUID,
    action VARCHAR(80) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id UUID,
    correlation_id VARCHAR(80),
    ip_address VARCHAR(64),
    user_agent VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX audit_logs_company_created_at_idx ON audit_logs (company_id, created_at DESC);
CREATE INDEX audit_logs_entity_idx ON audit_logs (company_id, entity_type, entity_id, created_at DESC);

CREATE FUNCTION prevent_audit_log_mutation() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'audit logs are append-only';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER audit_logs_no_update
BEFORE UPDATE OR DELETE ON audit_logs
FOR EACH ROW EXECUTE FUNCTION prevent_audit_log_mutation();
