CREATE TABLE users (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    username VARCHAR(80) NOT NULL,
    email VARCHAR(160) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    first_name VARCHAR(100) NOT NULL,
    last_name VARCHAR(100) NOT NULL,
    phone VARCHAR(40),
    status VARCHAR(24) NOT NULL,
    last_login_at TIMESTAMPTZ,
    password_changed_at TIMESTAMPTZ,
    must_change_password BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT users_status_check CHECK (status IN ('ACTIVE', 'PASSIVE', 'LOCKED')),
    CONSTRAINT users_company_username_unique UNIQUE (company_id, username),
    CONSTRAINT users_company_email_unique UNIQUE (company_id, email)
);

CREATE TABLE roles (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    name VARCHAR(40) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT roles_name_check CHECK (name IN ('ADMIN', 'ACCOUNTING', 'SALES', 'WAREHOUSE', 'VIEWER')),
    CONSTRAINT roles_company_name_unique UNIQUE (company_id, name)
);

CREATE TABLE permissions (
    id UUID PRIMARY KEY,
    code VARCHAR(80) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT permissions_code_unique UNIQUE (code)
);

CREATE TABLE user_roles (
    user_id UUID NOT NULL REFERENCES users (id),
    role_id UUID NOT NULL REFERENCES roles (id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE role_permissions (
    role_id UUID NOT NULL REFERENCES roles (id),
    permission_id UUID NOT NULL REFERENCES permissions (id),
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX users_company_status_idx ON users (company_id, status);
CREATE INDEX roles_company_idx ON roles (company_id);
