package com.yigitcicekci.tillora.search.infrastructure.persistence;

import com.yigitcicekci.tillora.search.api.response.SearchResultResponse;
import com.yigitcicekci.tillora.search.api.response.SearchResultType;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class SearchQueryRepository {

    private static final String CURRENT_ACCOUNT_QUERY = """
        SELECT
            'CURRENT_ACCOUNT' AS type,
            account.id,
            account.name AS title,
            account.legal_name AS subtitle,
            account.relationship_type AS reference,
            account.status,
            CASE
                WHEN lower(account.name) = lower(:term)
                  OR lower(coalesce(account.legal_name, '')) = lower(:term)
                  OR account.tax_number = :term
                  OR account.identity_number = :term
                THEN 0
                WHEN lower(account.name) LIKE lower(:prefix) ESCAPE '\\'
                  OR lower(coalesce(account.legal_name, '')) LIKE lower(:prefix) ESCAPE '\\'
                  OR account.tax_number LIKE :prefix ESCAPE '\\'
                  OR account.identity_number LIKE :prefix ESCAPE '\\'
                THEN 1
                ELSE 2
            END AS rank
        FROM current_accounts account
        WHERE account.company_id = :company_id
          AND lower(
              account.name || ' ' ||
              coalesce(account.legal_name, '') || ' ' ||
              coalesce(account.tax_number, '') || ' ' ||
              coalesce(account.identity_number, '')
          ) LIKE lower(:contains) ESCAPE '\\'
        ORDER BY rank, account.name, account.id
        LIMIT :candidate_limit
        """;

    private static final String PRODUCT_QUERY = """
        SELECT
            'PRODUCT' AS type,
            product.id,
            product.name AS title,
            product.code AS subtitle,
            product.barcode AS reference,
            CASE WHEN product.active THEN 'ACTIVE' ELSE 'PASSIVE' END AS status,
            CASE
                WHEN lower(product.code) = lower(:term)
                  OR lower(coalesce(product.barcode, '')) = lower(:term)
                  OR lower(product.name) = lower(:term)
                THEN 0
                WHEN lower(product.code) LIKE lower(:prefix) ESCAPE '\\'
                  OR lower(coalesce(product.barcode, '')) LIKE lower(:prefix) ESCAPE '\\'
                  OR lower(product.name) LIKE lower(:prefix) ESCAPE '\\'
                THEN 1
                ELSE 2
            END AS rank
        FROM products product
        WHERE product.company_id = :company_id
          AND lower(
              product.code || ' ' ||
              coalesce(product.barcode, '') || ' ' ||
              product.name
          ) LIKE lower(:contains) ESCAPE '\\'
        ORDER BY rank, product.name, product.id
        LIMIT :candidate_limit
        """;

    private static final String INVOICE_QUERY = """
        SELECT
            'DOCUMENT' AS type,
            invoice.id,
            invoice.invoice_number AS title,
            account.name AS subtitle,
            invoice.invoice_type AS reference,
            invoice.status,
            CASE
                WHEN lower(invoice.invoice_number) = lower(:term) THEN 0
                WHEN lower(invoice.invoice_number) LIKE lower(:prefix) ESCAPE '\\' THEN 1
                ELSE 2
            END AS rank
        FROM invoices invoice
        JOIN current_accounts account
          ON account.id = invoice.current_account_id
         AND account.company_id = invoice.company_id
        WHERE invoice.company_id = :company_id
          AND lower(invoice.invoice_number) LIKE lower(:contains) ESCAPE '\\'
        ORDER BY rank, invoice.invoice_date DESC, invoice.id
        LIMIT :candidate_limit
        """;

    private static final String RESULT_QUERY = """
        SELECT type, id, title, subtitle, reference, status
        FROM (
            SELECT
                matches.*,
                row_number() OVER (
                    PARTITION BY matches.type, matches.id
                    ORDER BY matches.rank
                ) AS result_order
            FROM (%s) matches
        ) ranked
        WHERE result_order = 1
        ORDER BY rank, title, id
        LIMIT :result_limit
        """;

    private final JdbcClient jdbcClient;

    public SearchQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public List<SearchResultResponse> search(
        UUID companyId,
        String term,
        EnumSet<SearchResultType> types,
        int limit
    ) {
        List<String> queries = new ArrayList<>();
        if (types.contains(SearchResultType.CURRENT_ACCOUNT)) {
            queries.add(CURRENT_ACCOUNT_QUERY);
        }
        if (types.contains(SearchResultType.PRODUCT)) {
            queries.add(PRODUCT_QUERY);
        }
        if (types.contains(SearchResultType.DOCUMENT)) {
            queries.add(INVOICE_QUERY);
        }
        if (queries.isEmpty()) {
            return List.of();
        }
        String escapedTerm = escapeLike(term);
        String unionQuery = queries.stream()
            .map(query -> "(" + query + ")")
            .reduce((left, right) -> left + "\nUNION ALL\n" + right)
            .orElseThrow();
        return jdbcClient.sql(RESULT_QUERY.formatted(unionQuery))
            .param("company_id", companyId)
            .param("term", term)
            .param("prefix", escapedTerm + "%")
            .param("contains", "%" + escapedTerm + "%")
            .param("candidate_limit", limit)
            .param("result_limit", limit)
            .query((result, rowNumber) -> new SearchResultResponse(
                SearchResultType.valueOf(result.getString("type")),
                result.getObject("id", UUID.class),
                result.getString("title"),
                result.getString("subtitle"),
                result.getString("reference"),
                result.getString("status")
            ))
            .list();
    }

    private String escapeLike(String value) {
        return value
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_");
    }
}
