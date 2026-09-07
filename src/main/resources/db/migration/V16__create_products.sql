WITH permission_mapping(role_name, permission_code) AS (
    VALUES
        ('ACCOUNTING', 'STOCK_READ'),
        ('SALES', 'STOCK_READ')
)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r
JOIN permission_mapping mapping ON mapping.role_name = r.name
JOIN permissions p ON p.code = mapping.permission_code
ON CONFLICT DO NOTHING;

CREATE TABLE products (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    code VARCHAR(64) NOT NULL,
    barcode VARCHAR(64),
    name VARCHAR(180) NOT NULL,
    unit VARCHAR(32) NOT NULL,
    purchase_price NUMERIC(19, 4) NOT NULL,
    sale_price NUMERIC(19, 4) NOT NULL,
    vat_rate NUMERIC(5, 2) NOT NULL,
    minimum_stock_level NUMERIC(19, 6) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT products_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT products_code_check CHECK (
        code = upper(btrim(code))
        AND code ~ '^[A-Z0-9._-]+$'
    ),
    CONSTRAINT products_barcode_check CHECK (
        barcode IS NULL
        OR (
            barcode = upper(btrim(barcode))
            AND barcode ~ '^[A-Z0-9._-]+$'
        )
    ),
    CONSTRAINT products_name_check CHECK (name = btrim(name) AND name <> ''),
    CONSTRAINT products_unit_check CHECK (
        unit IN (
            'PIECE',
            'KILOGRAM',
            'GRAM',
            'LITER',
            'MILLILITER',
            'METER',
            'SQUARE_METER',
            'CUBIC_METER',
            'PACKAGE',
            'BOX',
            'PAIR',
            'SET',
            'HOUR',
            'DAY'
        )
    ),
    CONSTRAINT products_purchase_price_check CHECK (purchase_price >= 0),
    CONSTRAINT products_sale_price_check CHECK (sale_price >= 0),
    CONSTRAINT products_vat_rate_check CHECK (vat_rate >= 0 AND vat_rate <= 100),
    CONSTRAINT products_minimum_stock_level_check CHECK (minimum_stock_level >= 0)
);

CREATE UNIQUE INDEX products_company_code_unique
    ON products (company_id, lower(code));

CREATE UNIQUE INDEX products_company_barcode_unique
    ON products (company_id, lower(barcode))
    WHERE barcode IS NOT NULL;

CREATE INDEX products_company_active_created_idx
    ON products (company_id, active, created_at DESC);
