INSERT INTO permissions (id, code)
VALUES ('00000000-0000-0000-0000-000000000038', 'DASHBOARD_VIEW')
ON CONFLICT (code) DO NOTHING;

WITH permission_mapping(role_name, permission_code) AS (
    VALUES
        ('ADMIN', 'DASHBOARD_VIEW'),
        ('ACCOUNTING', 'DASHBOARD_VIEW'),
        ('SALES', 'DASHBOARD_VIEW'),
        ('WAREHOUSE', 'DASHBOARD_VIEW'),
        ('VIEWER', 'DASHBOARD_VIEW')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permission_mapping mapping ON mapping.role_name = role.name
JOIN permissions permission ON permission.code = mapping.permission_code
ON CONFLICT DO NOTHING;

CREATE INDEX stock_balances_company_product_idx
    ON stock_balances (company_id, product_id);

CREATE INDEX electronic_documents_company_type_status_idx
    ON electronic_documents (company_id, document_type, status);

CREATE INDEX vouchers_company_approved_period_type_idx
    ON vouchers (company_id, voucher_date, voucher_type)
    WHERE status = 'APPROVED';
