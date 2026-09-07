ALTER TABLE stock_operations
    DROP CONSTRAINT stock_operations_type_check,
    ADD CONSTRAINT stock_operations_type_check
        CHECK (
            operation_type IN (
                'ADJUSTMENT',
                'TRANSFER',
                'SALES_INVOICE',
                'PURCHASE_INVOICE'
            )
        );
