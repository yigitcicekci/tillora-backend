INSERT INTO permissions (id, code)
VALUES
    ('00000000-0000-0000-0000-000000000033', 'EINVOICE_READ'),
    ('00000000-0000-0000-0000-000000000034', 'EINVOICE_PREPARE'),
    ('00000000-0000-0000-0000-000000000035', 'EINVOICE_REGISTER'),
    ('00000000-0000-0000-0000-000000000036', 'EINVOICE_UPLOAD'),
    ('00000000-0000-0000-0000-000000000037', 'EINVOICE_CANCEL')
ON CONFLICT (code) DO NOTHING;

WITH permission_mapping(role_name, permission_code) AS (
    VALUES
        ('ADMIN', 'EINVOICE_READ'),
        ('ADMIN', 'EINVOICE_PREPARE'),
        ('ADMIN', 'EINVOICE_REGISTER'),
        ('ADMIN', 'EINVOICE_UPLOAD'),
        ('ADMIN', 'EINVOICE_CANCEL'),
        ('ACCOUNTING', 'EINVOICE_READ'),
        ('ACCOUNTING', 'EINVOICE_PREPARE'),
        ('ACCOUNTING', 'EINVOICE_REGISTER'),
        ('ACCOUNTING', 'EINVOICE_UPLOAD'),
        ('ACCOUNTING', 'EINVOICE_CANCEL'),
        ('SALES', 'EINVOICE_READ'),
        ('SALES', 'EINVOICE_PREPARE'),
        ('SALES', 'EINVOICE_REGISTER'),
        ('SALES', 'EINVOICE_UPLOAD'),
        ('VIEWER', 'EINVOICE_READ')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT role.id, permission.id
FROM roles role
JOIN permission_mapping mapping ON mapping.role_name = role.name
JOIN permissions permission ON permission.code = mapping.permission_code
ON CONFLICT DO NOTHING;
