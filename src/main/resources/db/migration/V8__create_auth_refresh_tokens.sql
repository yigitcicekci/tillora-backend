CREATE TABLE auth_refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users (id),
    company_id UUID NOT NULL REFERENCES companies (id),
    token_hash VARCHAR(64) NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT auth_refresh_tokens_token_hash_unique UNIQUE (token_hash)
);

CREATE INDEX auth_refresh_tokens_user_active_idx ON auth_refresh_tokens (user_id, revoked_at, expires_at);
CREATE INDEX auth_refresh_tokens_company_idx ON auth_refresh_tokens (company_id);
