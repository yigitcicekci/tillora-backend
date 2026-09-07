ALTER TABLE users
    DROP CONSTRAINT users_company_username_unique;

DROP INDEX users_company_username_ci_unique;

CREATE UNIQUE INDEX users_username_ci_unique ON users (lower(username));
