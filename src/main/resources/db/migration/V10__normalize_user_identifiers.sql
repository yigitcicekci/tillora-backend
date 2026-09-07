UPDATE users
SET username = btrim(username),
    email = lower(btrim(email));

CREATE UNIQUE INDEX users_company_username_ci_unique ON users (company_id, lower(username));
CREATE UNIQUE INDEX users_company_email_ci_unique ON users (company_id, lower(email));

ALTER TABLE users
    ADD CONSTRAINT users_username_trimmed_check CHECK (username = btrim(username)),
    ADD CONSTRAINT users_email_normalized_check CHECK (email = lower(btrim(email)));

DELETE FROM role_permissions
WHERE permission_id = '00000000-0000-0000-0000-000000000002';

DELETE FROM permissions
WHERE id = '00000000-0000-0000-0000-000000000002'
  AND code = 'COMPANY_CREATE';
