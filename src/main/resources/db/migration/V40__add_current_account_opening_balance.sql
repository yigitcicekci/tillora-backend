ALTER TABLE current_account_movements
    DROP CONSTRAINT current_account_movements_type_check;

ALTER TABLE current_account_movements
    ADD CONSTRAINT current_account_movements_type_check CHECK (
        movement_type IN ('OPENING_BALANCE', 'SALES_INVOICE', 'PURCHASE_INVOICE', 'COLLECTION', 'PAYMENT')
    );

ALTER TABLE current_account_movements
    DROP CONSTRAINT current_account_movements_reference_type_check;

ALTER TABLE current_account_movements
    ADD CONSTRAINT current_account_movements_reference_type_check CHECK (
        reference_type IN ('CURRENT_ACCOUNT', 'INVOICE', 'VOUCHER')
    );

CREATE UNIQUE INDEX current_account_opening_balance_unique
    ON current_account_movements (company_id, current_account_id)
    WHERE movement_type = 'OPENING_BALANCE';
