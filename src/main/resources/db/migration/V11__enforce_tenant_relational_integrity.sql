ALTER TABLE users
    ADD CONSTRAINT users_id_company_unique UNIQUE (id, company_id);

ALTER TABLE roles
    ADD CONSTRAINT roles_id_company_unique UNIQUE (id, company_id);

ALTER TABLE chart_of_accounts
    ADD CONSTRAINT chart_of_accounts_id_company_unique UNIQUE (id, company_id),
    DROP CONSTRAINT chart_of_accounts_parent_id_fkey,
    ADD CONSTRAINT chart_of_accounts_parent_company_fk
        FOREIGN KEY (parent_id, company_id) REFERENCES chart_of_accounts (id, company_id);

ALTER TABLE current_accounts
    ADD CONSTRAINT current_accounts_id_company_unique UNIQUE (id, company_id);

ALTER TABLE current_account_ledger_accounts
    DROP CONSTRAINT current_account_ledger_accounts_current_account_id_fkey,
    DROP CONSTRAINT current_account_ledger_accounts_chart_of_account_id_fkey,
    ADD CONSTRAINT current_account_ledger_accounts_current_account_company_fk
        FOREIGN KEY (current_account_id, company_id) REFERENCES current_accounts (id, company_id),
    ADD CONSTRAINT current_account_ledger_accounts_chart_account_company_fk
        FOREIGN KEY (chart_of_account_id, company_id) REFERENCES chart_of_accounts (id, company_id);

ALTER TABLE account_code_sequences
    ADD CONSTRAINT account_code_sequences_chart_account_fk
        FOREIGN KEY (company_id, main_account_code) REFERENCES chart_of_accounts (company_id, code);

ALTER TABLE auth_refresh_tokens
    DROP CONSTRAINT auth_refresh_tokens_user_id_fkey,
    ADD CONSTRAINT auth_refresh_tokens_user_company_fk
        FOREIGN KEY (user_id, company_id) REFERENCES users (id, company_id);

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM user_roles ur
        JOIN users u ON u.id = ur.user_id
        JOIN roles r ON r.id = ur.role_id
        WHERE u.company_id <> r.company_id
    ) THEN
        RAISE EXCEPTION 'cross-company user role assignment exists';
    END IF;
END;
$$;

CREATE FUNCTION enforce_user_role_company() RETURNS trigger AS $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM users u
        JOIN roles r ON r.id = NEW.role_id
        WHERE u.id = NEW.user_id
          AND u.company_id = r.company_id
    ) THEN
        RAISE EXCEPTION 'user and role must belong to the same company' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER user_roles_company_check
BEFORE INSERT OR UPDATE ON user_roles
FOR EACH ROW EXECUTE FUNCTION enforce_user_role_company();
