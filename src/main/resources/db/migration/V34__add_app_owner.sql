ALTER TABLE users
    ADD COLUMN app_owner BOOLEAN NOT NULL DEFAULT false;

UPDATE users
SET app_owner = true
WHERE id = (
    SELECT user_account.id
    FROM users user_account
    JOIN user_roles mapping ON mapping.user_id = user_account.id
    JOIN roles role ON role.id = mapping.role_id
    WHERE role.name = 'ADMIN'
    ORDER BY user_account.created_at, user_account.id
    LIMIT 1
);

CREATE UNIQUE INDEX users_single_app_owner_idx ON users (app_owner)
WHERE app_owner = true;
