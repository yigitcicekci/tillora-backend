CREATE TABLE stock_settings (
    company_id UUID PRIMARY KEY REFERENCES companies (id),
    allow_negative_stock BOOLEAN NOT NULL DEFAULT false,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0
);

INSERT INTO stock_settings (company_id)
SELECT id
FROM companies
ON CONFLICT (company_id) DO NOTHING;

CREATE FUNCTION create_default_stock_setting()
RETURNS TRIGGER AS $$
BEGIN
    INSERT INTO stock_settings (company_id) VALUES (NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER companies_create_stock_setting
AFTER INSERT ON companies
FOR EACH ROW
EXECUTE FUNCTION create_default_stock_setting();

CREATE TABLE stock_operations (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    idempotency_key UUID NOT NULL,
    operation_type VARCHAR(24) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT stock_operations_id_company_unique UNIQUE (id, company_id),
    CONSTRAINT stock_operations_company_idempotency_unique UNIQUE (company_id, idempotency_key),
    CONSTRAINT stock_operations_type_check CHECK (operation_type IN ('ADJUSTMENT', 'TRANSFER')),
    CONSTRAINT stock_operations_fingerprint_check CHECK (request_fingerprint ~ '^[0-9a-f]{64}$')
);

CREATE TABLE stock_movements (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    operation_id UUID NOT NULL,
    product_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    movement_type VARCHAR(32) NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    unit_cost NUMERIC(19, 4) NOT NULL,
    reference_type VARCHAR(32) NOT NULL,
    reference_id UUID NOT NULL,
    movement_date DATE NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT stock_movements_operation_company_fk
        FOREIGN KEY (operation_id, company_id)
        REFERENCES stock_operations (id, company_id),
    CONSTRAINT stock_movements_product_company_fk
        FOREIGN KEY (product_id, company_id)
        REFERENCES products (id, company_id),
    CONSTRAINT stock_movements_warehouse_company_fk
        FOREIGN KEY (warehouse_id, company_id)
        REFERENCES warehouses (id, company_id),
    CONSTRAINT stock_movements_type_check CHECK (
        movement_type IN (
            'PURCHASE_ENTRY',
            'SALES_EXIT',
            'RETURN_ENTRY',
            'RETURN_EXIT',
            'ADJUSTMENT_ENTRY',
            'ADJUSTMENT_EXIT',
            'TRANSFER_IN',
            'TRANSFER_OUT'
        )
    ),
    CONSTRAINT stock_movements_quantity_check CHECK (quantity > 0),
    CONSTRAINT stock_movements_unit_cost_check CHECK (unit_cost >= 0),
    CONSTRAINT stock_movements_reference_type_check CHECK (
        reference_type IN (
            'MANUAL_ADJUSTMENT',
            'STOCK_TRANSFER',
            'PURCHASE_INVOICE',
            'SALES_INVOICE',
            'RETURN'
        )
    )
);

CREATE TABLE stock_balances (
    id UUID PRIMARY KEY,
    company_id UUID NOT NULL REFERENCES companies (id),
    product_id UUID NOT NULL,
    warehouse_id UUID NOT NULL,
    quantity NUMERIC(19, 6) NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT stock_balances_company_product_warehouse_unique
        UNIQUE (company_id, product_id, warehouse_id),
    CONSTRAINT stock_balances_product_company_fk
        FOREIGN KEY (product_id, company_id)
        REFERENCES products (id, company_id),
    CONSTRAINT stock_balances_warehouse_company_fk
        FOREIGN KEY (warehouse_id, company_id)
        REFERENCES warehouses (id, company_id)
);

CREATE INDEX stock_movements_company_date_idx
    ON stock_movements (company_id, movement_date DESC, created_at DESC);

CREATE INDEX stock_movements_company_product_date_idx
    ON stock_movements (company_id, product_id, movement_date DESC);

CREATE INDEX stock_movements_company_warehouse_date_idx
    ON stock_movements (company_id, warehouse_id, movement_date DESC);

CREATE INDEX stock_balances_company_warehouse_idx
    ON stock_balances (company_id, warehouse_id, product_id);

CREATE FUNCTION prevent_stock_movement_mutation()
RETURNS TRIGGER AS $$
BEGIN
    RAISE EXCEPTION USING
        MESSAGE = 'Stock movements are immutable',
        ERRCODE = '23514';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER stock_movements_immutable
BEFORE UPDATE OR DELETE ON stock_movements
FOR EACH ROW
EXECUTE FUNCTION prevent_stock_movement_mutation();
