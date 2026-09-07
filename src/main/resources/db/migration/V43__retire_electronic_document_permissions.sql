DELETE FROM role_permissions
WHERE permission_id IN (
    SELECT id
    FROM permissions
    WHERE code IN (
        'COMPANY_MANAGE',
        'INVOICE_EMAIL_SEND',
        'EARCHIVE_CREATE',
        'EARCHIVE_SIGN',
        'EARCHIVE_CANCEL',
        'EINVOICE_READ',
        'EINVOICE_PREPARE',
        'EINVOICE_REGISTER',
        'EINVOICE_UPLOAD',
        'EINVOICE_CANCEL'
    )
);

DELETE FROM permissions
WHERE code IN (
    'COMPANY_MANAGE',
    'INVOICE_EMAIL_SEND',
    'EARCHIVE_CREATE',
    'EARCHIVE_SIGN',
    'EARCHIVE_CANCEL',
    'EINVOICE_READ',
    'EINVOICE_PREPARE',
    'EINVOICE_REGISTER',
    'EINVOICE_UPLOAD',
    'EINVOICE_CANCEL'
);
