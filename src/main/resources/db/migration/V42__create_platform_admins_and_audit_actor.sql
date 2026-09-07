CREATE TABLE platform_admins (
    id UUID PRIMARY KEY,
    email VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    last_login_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT platform_admins_email_trimmed_check CHECK (email = btrim(email)),
    CONSTRAINT platform_admins_email_normalized_check CHECK (email = lower(email))
);

CREATE UNIQUE INDEX platform_admins_email_ci_unique ON platform_admins (lower(email));
CREATE INDEX platform_admins_enabled_idx ON platform_admins (enabled);

CREATE TABLE platform_admin_refresh_tokens (
    id UUID PRIMARY KEY,
    platform_admin_id UUID NOT NULL REFERENCES platform_admins (id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT platform_admin_refresh_tokens_hash_unique UNIQUE (token_hash)
);

CREATE INDEX platform_admin_refresh_tokens_admin_active_idx
    ON platform_admin_refresh_tokens (platform_admin_id, revoked_at, expires_at);

ALTER TABLE audit_logs
    ADD COLUMN platform_admin_id UUID REFERENCES platform_admins (id),
    ADD COLUMN result VARCHAR(24);

CREATE INDEX audit_logs_platform_admin_created_idx
    ON audit_logs (platform_admin_id, created_at DESC)
    WHERE platform_admin_id IS NOT NULL;
