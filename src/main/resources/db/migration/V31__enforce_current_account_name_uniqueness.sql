CREATE UNIQUE INDEX current_accounts_company_customer_name_ci_unique
    ON current_accounts (company_id, lower(btrim(name)))
    WHERE relationship_type IN ('CUSTOMER', 'BOTH');

CREATE UNIQUE INDEX current_accounts_company_supplier_name_ci_unique
    ON current_accounts (company_id, lower(btrim(name)))
    WHERE relationship_type IN ('SUPPLIER', 'BOTH');
