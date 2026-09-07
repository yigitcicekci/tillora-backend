ALTER TABLE invoices
    DROP CONSTRAINT invoices_warehouse_company_fk,
    DROP COLUMN warehouse_id;

DROP TRIGGER companies_create_stock_setting ON companies;
DROP FUNCTION create_default_stock_setting();

DROP TABLE stock_movements;
DROP FUNCTION prevent_stock_movement_mutation();
DROP TABLE stock_balances;
DROP TABLE stock_operations;
DROP TABLE stock_settings;
DROP TABLE warehouses;

ALTER TABLE products
    DROP CONSTRAINT products_minimum_stock_level_check,
    DROP COLUMN minimum_stock_level;

INSERT INTO permissions (id, code)
VALUES
    ('00000000-0000-0000-0000-000000000040', 'PRODUCT_READ'),
    ('00000000-0000-0000-0000-000000000041', 'PRODUCT_MANAGE')
ON CONFLICT (code) DO NOTHING;

WITH permission_mapping(role_name, permission_code) AS (
    VALUES
        ('ADMIN', 'PRODUCT_READ'),
        ('ADMIN', 'PRODUCT_MANAGE'),
        ('ACCOUNTING', 'PRODUCT_READ'),
        ('SALES', 'PRODUCT_READ'),
        ('VIEWER', 'PRODUCT_READ')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permission_mapping mapping ON mapping.role_name = role.name
JOIN permissions permission ON permission.code = mapping.permission_code
ON CONFLICT DO NOTHING;

DELETE FROM user_roles
WHERE role_id IN (SELECT id FROM roles WHERE name = 'WAREHOUSE');

DELETE FROM role_permissions
WHERE role_id IN (SELECT id FROM roles WHERE name = 'WAREHOUSE')
   OR permission_id IN (
       SELECT id FROM permissions WHERE code IN ('STOCK_READ', 'STOCK_ADJUST')
   );

DELETE FROM roles WHERE name = 'WAREHOUSE';
DELETE FROM permissions WHERE code IN ('STOCK_READ', 'STOCK_ADJUST');

ALTER TABLE roles
    DROP CONSTRAINT roles_name_check,
    ADD CONSTRAINT roles_name_check
        CHECK (name IN ('ADMIN', 'ACCOUNTING', 'SALES', 'VIEWER'));
