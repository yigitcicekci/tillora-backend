package com.yigitcicekci.tillora.reporting.infrastructure.persistence;

import com.yigitcicekci.tillora.reporting.api.response.AuditReportRow;
import com.yigitcicekci.tillora.reporting.api.response.CurrentAccountBalanceRow;
import com.yigitcicekci.tillora.reporting.api.response.CurrentAccountStatementRow;
import com.yigitcicekci.tillora.reporting.api.response.InvoiceReportRow;
import com.yigitcicekci.tillora.reporting.api.response.SettlementMovementReportRow;
import com.yigitcicekci.tillora.reporting.api.response.VoucherReportRow;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ReportingQueryRepository {

    private static final Map<String, String> STATEMENT_SORTS = Map.of(
        "voucherDate", "voucher_date",
        "voucherNumber", "voucher_number",
        "debit", "debit",
        "credit", "credit"
    );
    private static final Map<String, String> BALANCE_SORTS = Map.of(
        "name", "name",
        "balance", "balance",
        "overdueAmount", "overdue_amount",
        "nearestDueDate", "nearest_due_date"
    );
    private static final Map<String, String> VOUCHER_SORTS = Map.of(
        "voucherDate", "voucher_date",
        "voucherNumber", "voucher_number",
        "voucherType", "voucher_type",
        "status", "status",
        "totalDebit", "total_debit"
    );
    private static final Map<String, String> SETTLEMENT_SORTS = Map.of(
        "voucherDate", "voucher_date",
        "voucherNumber", "voucher_number",
        "accountName", "account_name",
        "debit", "debit",
        "credit", "credit"
    );
    private static final Map<String, String> INVOICE_SORTS = Map.of(
        "invoiceDate", "invoice_date",
        "invoiceNumber", "invoice_number",
        "currentAccountName", "current_account_name",
        "status", "status",
        "grandTotal", "grand_total"
    );
    private static final Map<String, String> AUDIT_SORTS = Map.of(
        "createdAt", "created_at",
        "username", "username",
        "action", "action",
        "entityType", "entity_type"
    );

    private final JdbcClient jdbcClient;

    public ReportingQueryRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public Page<CurrentAccountStatementRow> currentAccountStatement(
        UUID companyId,
        UUID currentAccountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        return currentAccountStatement(companyId, currentAccountId, dateFrom, dateTo, pageable, Locale.ENGLISH);
    }

    public Page<CurrentAccountStatementRow> currentAccountStatement(
        UUID companyId,
        UUID currentAccountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable,
        Locale locale
    ) {
        Map<String, Object> params = parameters(companyId, dateFrom, dateTo);
        params.put("current_account_id", currentAccountId);
        String source = """
            FROM (
                SELECT opening.id AS voucher_id,
                       'DEVIR-' || substring(replace(opening.id::text, '-', ''), 1, 12) AS voucher_number,
                       %3$s AS voucher_type,
                       opening.movement_date AS voucher_date,
                       NULL::varchar AS document_number,
                       %4$s AS movement_note,
                       opening.debit,
                       opening.credit,
                       opening.debit - opening.credit AS balance,
                       company.currency,
                       CAST(1.00000000 AS numeric(20, 8)) AS exchange_rate
                FROM current_account_movements opening
                JOIN companies company ON company.id = opening.company_id
                WHERE opening.company_id = :company_id
                  AND opening.current_account_id = :current_account_id
                  AND opening.movement_type = 'OPENING_BALANCE'
                  AND opening.movement_date <= :date_to
                UNION ALL
                SELECT voucher.id AS voucher_id,
                       voucher.voucher_number,
                       %1$s AS voucher_type,
                       voucher.voucher_date,
                       voucher.document_number,
                       %2$s AS movement_note,
                       line.debit,
                       line.credit,
                       sum((line.debit - line.credit) * voucher.exchange_rate) OVER (
                           ORDER BY voucher.voucher_date, voucher.voucher_number, line.line_number
                       ) + opening.balance AS balance,
                       voucher.currency,
                       voucher.exchange_rate
                FROM voucher_lines line
                JOIN vouchers voucher ON voucher.id = line.voucher_id
                    AND voucher.company_id = line.company_id
                CROSS JOIN (
                    SELECT coalesce(sum(movement.debit - movement.credit), 0) AS balance
                    FROM current_account_movements movement
                    WHERE movement.company_id = :company_id
                      AND movement.current_account_id = :current_account_id
                      AND movement.movement_type = 'OPENING_BALANCE'
                      AND movement.movement_date <= :date_to
                ) opening
                WHERE line.company_id = :company_id
                  AND line.current_account_id = :current_account_id
                  AND voucher.status = 'APPROVED'
                  AND voucher.voucher_date <= :date_to
            ) report
            WHERE voucher_date >= :date_from
            """.formatted(
                voucherTypeLabel("voucher", locale),
                movementNoteLabel("voucher", "line", locale),
                sqlLiteral(openingBalanceLabel(locale)),
                sqlLiteral(openingBalanceLabel(locale))
            );
        return page("SELECT * ", source, params, pageable, STATEMENT_SORTS,
            "voucher_date ASC, voucher_number ASC", this::statementRow);
    }

    public Page<CurrentAccountBalanceRow> currentAccountBalances(
        UUID companyId,
        boolean receivables,
        LocalDate dateFrom,
        LocalDate dateTo,
        LocalDate today,
        Pageable pageable
    ) {
        Map<String, Object> params = parameters(companyId, dateFrom, dateTo);
        params.put("today", today);
        String direction = receivables ? "line.debit - line.credit" : "line.credit - line.debit";
        String openingDirection = receivables ? "movement.debit - movement.credit" : "movement.credit - movement.debit";
        String overdue = receivables
            ? "sum(CASE WHEN line.due_date < :today THEN line.debit * voucher.exchange_rate ELSE 0 END) - sum(line.credit * voucher.exchange_rate)"
            : "sum(CASE WHEN line.due_date < :today THEN line.credit * voucher.exchange_rate ELSE 0 END) - sum(line.debit * voucher.exchange_rate)";
        String relationship = receivables ? "('CUSTOMER', 'BOTH')" : "('SUPPLIER', 'BOTH')";
        String source = """
            FROM (
                SELECT account.id AS current_account_id,
                       account.name,
                       account.relationship_type,
                       coalesce(voucher_balance.balance, 0) + coalesce(opening_balance.balance, 0) AS balance,
                       greatest(coalesce(voucher_balance.overdue_amount, 0), 0) AS overdue_amount,
                       voucher_balance.nearest_due_date
                FROM current_accounts account
                LEFT JOIN (
                    SELECT line.current_account_id,
                           sum((%s) * voucher.exchange_rate) AS balance,
                           greatest(%s, 0) AS overdue_amount,
                           min(line.due_date) FILTER (WHERE line.due_date >= :today) AS nearest_due_date
                    FROM voucher_lines line
                    JOIN vouchers voucher ON voucher.id = line.voucher_id
                        AND voucher.company_id = line.company_id
                    WHERE line.company_id = :company_id
                      AND voucher.status = 'APPROVED'
                      AND voucher.voucher_date BETWEEN :date_from AND :date_to
                    GROUP BY line.current_account_id
                ) voucher_balance ON voucher_balance.current_account_id = account.id
                LEFT JOIN (
                    SELECT movement.current_account_id,
                           sum(%s) AS balance
                    FROM current_account_movements movement
                    WHERE movement.company_id = :company_id
                      AND movement.movement_type = 'OPENING_BALANCE'
                    GROUP BY movement.current_account_id
                ) opening_balance ON opening_balance.current_account_id = account.id
                WHERE account.company_id = :company_id
                  AND account.relationship_type IN %s
                  AND coalesce(voucher_balance.balance, 0) + coalesce(opening_balance.balance, 0) > 0
            ) report
            """.formatted(direction, overdue, openingDirection, relationship);
        return page("SELECT * ", source, params, pageable, BALANCE_SORTS,
            "balance DESC, name ASC", this::balanceRow);
    }

    public Page<VoucherReportRow> vouchers(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String voucherType,
        String status,
        Pageable pageable
    ) {
        return vouchers(companyId, dateFrom, dateTo, voucherType, status, pageable, Locale.ENGLISH);
    }

    public Page<VoucherReportRow> vouchers(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String voucherType,
        String status,
        Pageable pageable,
        Locale locale
    ) {
        Map<String, Object> params = parameters(companyId, dateFrom, dateTo);
        StringBuilder source = new StringBuilder("""
            FROM vouchers vouchers
            WHERE vouchers.company_id = :company_id
              AND vouchers.voucher_date BETWEEN :date_from AND :date_to
            """);
        optional(source, params, "voucher_type", voucherType);
        optional(source, params, "status", status);
        return page("""
            SELECT vouchers.id, vouchers.voucher_number,
                   %s AS voucher_type,
                   vouchers.voucher_date, vouchers.status,
                   vouchers.document_number,
                   %s AS movement_note,
                   vouchers.currency, vouchers.exchange_rate,
                   vouchers.total_debit, vouchers.total_credit
            """.formatted(
                voucherTypeLabel("vouchers", locale),
                movementNoteLabel("vouchers", null, locale)
            ), source.toString(), params, pageable, VOUCHER_SORTS,
            "voucher_date DESC, voucher_number DESC", this::voucherRow);
    }

    public Page<SettlementMovementReportRow> settlementMovements(
        UUID companyId,
        boolean cash,
        UUID accountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        return settlementMovements(companyId, cash, accountId, dateFrom, dateTo, pageable, Locale.ENGLISH);
    }

    public Page<SettlementMovementReportRow> settlementMovements(
        UUID companyId,
        boolean cash,
        UUID accountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable,
        Locale locale
    ) {
        Map<String, Object> params = parameters(companyId, dateFrom, dateTo);
        String table = cash ? "cash_accounts" : "bank_accounts";
        StringBuilder source = new StringBuilder("""
            FROM voucher_lines line
            JOIN vouchers voucher ON voucher.id = line.voucher_id
                AND voucher.company_id = line.company_id
            JOIN %s account ON account.company_id = line.company_id
                AND account.chart_of_account_id = line.chart_of_account_id
            WHERE line.company_id = :company_id
              AND voucher.status = 'APPROVED'
              AND voucher.voucher_date BETWEEN :date_from AND :date_to
            """.formatted(table));
        if (accountId != null) {
            source.append(" AND account.id = :account_id");
            params.put("account_id", accountId);
        }
        return page("""
            SELECT account.id AS account_id, account.name AS account_name,
                   account.account_code, voucher.id AS voucher_id,
                   voucher.voucher_number,
                   %s AS voucher_type,
                   voucher.voucher_date,
                   %s AS movement_note,
                   line.debit, line.credit,
                   (line.debit - line.credit) * voucher.exchange_rate AS balance_change,
                   voucher.currency, voucher.exchange_rate
            """.formatted(
                voucherTypeLabel("voucher", locale),
                movementNoteLabel("voucher", "line", locale)
            ), source.toString(), params, pageable, SETTLEMENT_SORTS,
            "voucher_date DESC, voucher_number DESC", this::settlementRow);
    }

    public Page<InvoiceReportRow> invoices(
        UUID companyId,
        String invoiceType,
        LocalDate dateFrom,
        LocalDate dateTo,
        String status,
        Pageable pageable
    ) {
        Map<String, Object> params = parameters(companyId, dateFrom, dateTo);
        params.put("invoice_type", invoiceType);
        StringBuilder source = new StringBuilder("""
            FROM invoices invoice
            JOIN current_accounts account ON account.id = invoice.current_account_id
                AND account.company_id = invoice.company_id
            WHERE invoice.company_id = :company_id
              AND invoice.invoice_type = :invoice_type
              AND invoice.invoice_date BETWEEN :date_from AND :date_to
            """);
        optional(source, params, "invoice.status", "status", status);
        return page("""
            SELECT invoice.id, invoice.invoice_number, invoice.invoice_date, invoice.due_date,
                   account.id AS current_account_id, account.name AS current_account_name,
                   invoice.status, invoice.currency, invoice.exchange_rate, invoice.subtotal,
                   invoice.discount_total, invoice.tax_total, invoice.grand_total
            """, source.toString(), params, pageable, INVOICE_SORTS,
            "invoice_date DESC, invoice_number DESC", this::invoiceRow);
    }

    public Page<AuditReportRow> audit(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String action,
        String entityType,
        Pageable pageable
    ) {
        Map<String, Object> params = parameters(companyId, dateFrom, dateTo);
        StringBuilder source = new StringBuilder("""
            FROM audit_logs audit
            LEFT JOIN users app_user ON app_user.id = audit.user_id
                AND app_user.company_id = audit.company_id
            WHERE audit.company_id = :company_id
              AND audit.created_at::date BETWEEN :date_from AND :date_to
            """);
        optional(source, params, "audit.action", "action", action);
        optional(source, params, "audit.entity_type", "entity_type", entityType);
        return page("""
            SELECT audit.id, audit.user_id, app_user.username, audit.action,
                   audit.entity_type, audit.entity_id, audit.correlation_id,
                   audit.ip_address, audit.created_at
            """, source.toString(), params, pageable, AUDIT_SORTS,
            "created_at DESC, id DESC", this::auditRow);
    }

    private String voucherTypeLabel(String alias, Locale locale) {
        return """
            CASE %s.voucher_type
                WHEN 'COLLECTION' THEN %s
                WHEN 'PAYMENT' THEN %s
                WHEN 'OFFSET' THEN %s
                WHEN 'TRANSFER' THEN %s
                ELSE %s.voucher_type
            END
            """.formatted(
            alias,
            sqlLiteral(localize(locale, "Tahsilat", "Collection")),
            sqlLiteral(localize(locale, "Tediye", "Payment")),
            sqlLiteral(localize(locale, "Mahsup", "Offset")),
            sqlLiteral(localize(locale, "Virman", "Transfer")),
            alias
        );
    }

    private String movementNoteLabel(String voucherAlias, String lineAlias, Locale locale) {
        String fallback = lineAlias == null
            ? voucherAlias + ".movement_note"
            : "coalesce(" + lineAlias + ".movement_note, " + voucherAlias + ".movement_note)";
        return """
            CASE %s.source_type
                WHEN 'SALES_INVOICE' THEN %s || ' ' || coalesce(%s.document_number, '')
                WHEN 'PURCHASE_INVOICE' THEN %s || ' ' || coalesce(%s.document_number, '')
                ELSE %s
            END
            """.formatted(
            voucherAlias,
            sqlLiteral(localize(locale, "Satış faturası", "Sales invoice")),
            voucherAlias,
            sqlLiteral(localize(locale, "Alış faturası", "Purchase invoice")),
            voucherAlias,
            fallback
        );
    }

    private String openingBalanceLabel(Locale locale) {
        return localize(locale, "Açılış bakiyesi", "Opening balance");
    }

    private String localize(Locale locale, String turkish, String english) {
        return locale != null && "tr".equals(locale.getLanguage()) ? turkish : english;
    }

    private String sqlLiteral(String value) {
        return "'" + value.replace("'", "''") + "'";
    }

    private <T> Page<T> page(
        String select,
        String source,
        Map<String, Object> params,
        Pageable pageable,
        Map<String, String> allowedSorts,
        String defaultSort,
        RowMapper<T> rowMapper
    ) {
        String orderBy = orderBy(pageable, allowedSorts, defaultSort);
        Map<String, Object> pageParams = new LinkedHashMap<>(params);
        pageParams.put("limit", pageable.getPageSize());
        pageParams.put("offset", pageable.getOffset());
        var rows = jdbcClient.sql(select + source + " ORDER BY " + orderBy + " LIMIT :limit OFFSET :offset")
            .params(pageParams)
            .query(rowMapper)
            .list();
        long total = jdbcClient.sql("SELECT count(*) " + source)
            .params(params)
            .query(Long.class)
            .single();
        return new PageImpl<>(rows, pageable, total);
    }

    private String orderBy(Pageable pageable, Map<String, String> allowedSorts, String defaultSort) {
        if (pageable.getSort().isUnsorted()) {
            return defaultSort;
        }
        return pageable.getSort().stream()
            .map(order -> {
                String column = allowedSorts.get(order.getProperty());
                if (column == null) {
                    throw new BusinessException(
                        "REPORT_SORT_INVALID",
                        "Unsupported report sort property: " + order.getProperty(),
                        HttpStatus.BAD_REQUEST
                    );
                }
                return column + " " + order.getDirection().name();
            })
            .reduce((left, right) -> left + ", " + right)
            .orElse(defaultSort);
    }

    private Map<String, Object> parameters(UUID companyId, LocalDate dateFrom, LocalDate dateTo) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("company_id", companyId);
        params.put("date_from", dateFrom);
        params.put("date_to", dateTo);
        return params;
    }

    private void optional(StringBuilder source, Map<String, Object> params, String column, String value) {
        optional(source, params, column, column, value);
    }

    private void optional(
        StringBuilder source,
        Map<String, Object> params,
        String column,
        String parameter,
        String value
    ) {
        if (value != null) {
            source.append(" AND ").append(column).append(" = :").append(parameter);
            params.put(parameter, value);
        }
    }

    private CurrentAccountStatementRow statementRow(ResultSet result, int row) throws SQLException {
        return new CurrentAccountStatementRow(
            result.getObject("voucher_id", UUID.class),
            result.getString("voucher_number"),
            result.getString("voucher_type"),
            result.getObject("voucher_date", LocalDate.class),
            result.getString("document_number"),
            result.getString("movement_note"),
            result.getBigDecimal("debit"),
            result.getBigDecimal("credit"),
            result.getBigDecimal("balance"),
            result.getString("currency"),
            result.getBigDecimal("exchange_rate")
        );
    }

    private CurrentAccountBalanceRow balanceRow(ResultSet result, int row) throws SQLException {
        return new CurrentAccountBalanceRow(
            result.getObject("current_account_id", UUID.class),
            result.getString("name"),
            result.getString("relationship_type"),
            result.getBigDecimal("balance"),
            result.getBigDecimal("overdue_amount"),
            result.getObject("nearest_due_date", LocalDate.class)
        );
    }

    private VoucherReportRow voucherRow(ResultSet result, int row) throws SQLException {
        return new VoucherReportRow(
            result.getObject("id", UUID.class),
            result.getString("voucher_number"),
            result.getString("voucher_type"),
            result.getObject("voucher_date", LocalDate.class),
            result.getString("status"),
            result.getString("document_number"),
            result.getString("movement_note"),
            result.getString("currency"),
            result.getBigDecimal("exchange_rate"),
            result.getBigDecimal("total_debit"),
            result.getBigDecimal("total_credit")
        );
    }

    private SettlementMovementReportRow settlementRow(ResultSet result, int row) throws SQLException {
        return new SettlementMovementReportRow(
            result.getObject("account_id", UUID.class),
            result.getString("account_name"),
            result.getString("account_code"),
            result.getObject("voucher_id", UUID.class),
            result.getString("voucher_number"),
            result.getString("voucher_type"),
            result.getObject("voucher_date", LocalDate.class),
            result.getString("movement_note"),
            result.getBigDecimal("debit"),
            result.getBigDecimal("credit"),
            result.getBigDecimal("balance_change"),
            result.getString("currency"),
            result.getBigDecimal("exchange_rate")
        );
    }

    private InvoiceReportRow invoiceRow(ResultSet result, int row) throws SQLException {
        return new InvoiceReportRow(
            result.getObject("id", UUID.class),
            result.getString("invoice_number"),
            result.getObject("invoice_date", LocalDate.class),
            result.getObject("due_date", LocalDate.class),
            result.getObject("current_account_id", UUID.class),
            result.getString("current_account_name"),
            result.getString("status"),
            result.getString("currency"),
            result.getBigDecimal("exchange_rate"),
            result.getBigDecimal("subtotal"),
            result.getBigDecimal("discount_total"),
            result.getBigDecimal("tax_total"),
            result.getBigDecimal("grand_total")
        );
    }

    private AuditReportRow auditRow(ResultSet result, int row) throws SQLException {
        return new AuditReportRow(
            result.getObject("id", UUID.class),
            result.getObject("user_id", UUID.class),
            result.getString("username"),
            result.getString("action"),
            result.getString("entity_type"),
            result.getObject("entity_id", UUID.class),
            result.getString("correlation_id"),
            result.getString("ip_address"),
            instant(result, "created_at")
        );
    }

    private Instant instant(ResultSet result, String column) throws SQLException {
        var timestamp = result.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }
}
