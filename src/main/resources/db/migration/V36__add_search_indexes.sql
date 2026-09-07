CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE INDEX current_accounts_search_trgm_idx
    ON current_accounts USING gin (
        lower(
            name || ' ' ||
            coalesce(legal_name, '') || ' ' ||
            coalesce(tax_number, '') || ' ' ||
            coalesce(identity_number, '')
        ) gin_trgm_ops
    );

CREATE INDEX products_search_trgm_idx
    ON products USING gin (
        lower(
            code || ' ' ||
            coalesce(barcode, '') || ' ' ||
            name
        ) gin_trgm_ops
    );

CREATE INDEX invoices_number_search_trgm_idx
    ON invoices USING gin (lower(invoice_number) gin_trgm_ops);

CREATE INDEX electronic_documents_search_trgm_idx
    ON electronic_documents USING gin (
        lower(
            coalesce(official_number, '') || ' ' ||
            ettn::text
        ) gin_trgm_ops
    );
