package com.yigitcicekci.tillora.reporting.application.service;

import com.yigitcicekci.tillora.company.application.service.CompanyFinancialContext;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.reporting.api.response.AuditReportRow;
import com.yigitcicekci.tillora.reporting.api.response.CurrentAccountBalanceRow;
import com.yigitcicekci.tillora.reporting.api.response.CurrentAccountStatementRow;
import com.yigitcicekci.tillora.reporting.api.response.InvoiceReportRow;
import com.yigitcicekci.tillora.reporting.api.response.SettlementMovementReportRow;
import com.yigitcicekci.tillora.reporting.api.response.VoucherReportRow;
import com.yigitcicekci.tillora.reporting.infrastructure.persistence.ReportingQueryRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ReportingService {

    private static final LocalDate MIN_DATE = LocalDate.of(2000, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(9999, 12, 31);
    private static final int EXPORT_PAGE_SIZE = 100;
    private static final int MAX_EXPORT_ROWS = 10_000;
    private static final Set<String> VOUCHER_TYPES = Set.of("COLLECTION", "PAYMENT", "OFFSET", "TRANSFER");
    private static final Set<String> VOUCHER_STATUSES = Set.of("DRAFT", "APPROVED", "CANCELLED");
    private static final Set<String> INVOICE_STATUSES = Set.of("DRAFT", "APPROVED", "CANCELLED");

    private final ReportingQueryRepository queryRepository;
    private final CompanyService companyService;

    public ReportingService(ReportingQueryRepository queryRepository, CompanyService companyService) {
        this.queryRepository = queryRepository;
        this.companyService = companyService;
    }

    public Page<CurrentAccountStatementRow> currentAccountStatement(
        UUID companyId,
        UUID currentAccountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        DateRange range = range(dateFrom, dateTo);
        validate(pageable);
        return queryRepository.currentAccountStatement(
            companyId, currentAccountId, range.from(), range.to(), pageable
        );
    }

    public Page<CurrentAccountStatementRow> currentAccountStatement(
        UUID companyId,
        UUID currentAccountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable,
        Locale locale
    ) {
        DateRange range = range(dateFrom, dateTo);
        validate(pageable);
        return queryRepository.currentAccountStatement(
            companyId, currentAccountId, range.from(), range.to(), pageable, locale
        );
    }

    public List<CurrentAccountStatementExportRow> currentAccountStatementForExport(
        UUID companyId,
        UUID currentAccountId,
        LocalDate dateFrom,
        LocalDate dateTo
    ) {
        DateRange range = range(dateFrom, dateTo);
        List<CurrentAccountStatementExportRow> rows = new ArrayList<>();
        int pageNumber = 0;
        Page<CurrentAccountStatementRow> page;
        do {
            page = queryRepository.currentAccountStatement(
                companyId,
                currentAccountId,
                range.from(),
                range.to(),
                PageRequest.of(pageNumber++, EXPORT_PAGE_SIZE)
            );
            rows.addAll(page.getContent().stream()
                .map(CurrentAccountStatementExportRow::from)
                .toList());
            if (rows.size() > MAX_EXPORT_ROWS) {
                throw new BusinessException(
                    "REPORT_EXPORT_TOO_LARGE",
                    "Report export contains too many rows.",
                    HttpStatus.PAYLOAD_TOO_LARGE
                );
            }
        } while (page.hasNext());
        return List.copyOf(rows);
    }

    public List<CurrentAccountStatementExportRow> currentAccountStatementForExport(
        UUID companyId,
        UUID currentAccountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Locale locale
    ) {
        DateRange range = range(dateFrom, dateTo);
        List<CurrentAccountStatementExportRow> rows = new ArrayList<>();
        int pageNumber = 0;
        Page<CurrentAccountStatementRow> page;
        do {
            page = queryRepository.currentAccountStatement(
                companyId,
                currentAccountId,
                range.from(),
                range.to(),
                PageRequest.of(pageNumber++, EXPORT_PAGE_SIZE),
                locale
            );
            rows.addAll(page.getContent().stream()
                .map(CurrentAccountStatementExportRow::from)
                .toList());
            if (rows.size() > MAX_EXPORT_ROWS) {
                throw new BusinessException(
                    "REPORT_EXPORT_TOO_LARGE",
                    "Report export contains too many rows.",
                    HttpStatus.PAYLOAD_TOO_LARGE
                );
            }
        } while (page.hasNext());
        return List.copyOf(rows);
    }

    public Page<CurrentAccountBalanceRow> receivables(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        return balances(companyId, true, dateFrom, dateTo, pageable);
    }

    public Page<CurrentAccountBalanceRow> payables(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        return balances(companyId, false, dateFrom, dateTo, pageable);
    }

    public Page<VoucherReportRow> vouchers(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String voucherType,
        String status,
        Pageable pageable
    ) {
        DateRange range = range(dateFrom, dateTo);
        validateValue(voucherType, VOUCHER_TYPES, "voucherType");
        validateValue(status, VOUCHER_STATUSES, "status");
        validate(pageable);
        return queryRepository.vouchers(
            companyId, range.from(), range.to(), voucherType, status, pageable
        );
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
        DateRange range = range(dateFrom, dateTo);
        validateValue(voucherType, VOUCHER_TYPES, "voucherType");
        validateValue(status, VOUCHER_STATUSES, "status");
        validate(pageable);
        return queryRepository.vouchers(
            companyId, range.from(), range.to(), voucherType, status, pageable, locale
        );
    }

    public Page<SettlementMovementReportRow> cashMovements(
        UUID companyId,
        UUID accountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        return settlementMovements(companyId, true, accountId, dateFrom, dateTo, pageable);
    }

    public Page<SettlementMovementReportRow> cashMovements(
        UUID companyId,
        UUID accountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable,
        Locale locale
    ) {
        return settlementMovements(companyId, true, accountId, dateFrom, dateTo, pageable, locale);
    }

    public Page<SettlementMovementReportRow> bankMovements(
        UUID companyId,
        UUID accountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        return settlementMovements(companyId, false, accountId, dateFrom, dateTo, pageable);
    }

    public Page<SettlementMovementReportRow> bankMovements(
        UUID companyId,
        UUID accountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable,
        Locale locale
    ) {
        return settlementMovements(companyId, false, accountId, dateFrom, dateTo, pageable, locale);
    }

    public Page<InvoiceReportRow> sales(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String status,
        Pageable pageable
    ) {
        return invoices(companyId, "SALES", dateFrom, dateTo, status, pageable);
    }

    public Page<InvoiceReportRow> purchases(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String status,
        Pageable pageable
    ) {
        return invoices(companyId, "PURCHASE", dateFrom, dateTo, status, pageable);
    }

    public Page<AuditReportRow> audit(
        UUID companyId,
        LocalDate dateFrom,
        LocalDate dateTo,
        String action,
        String entityType,
        Pageable pageable
    ) {
        DateRange range = range(dateFrom, dateTo);
        validateText(action, "action");
        validateText(entityType, "entityType");
        validate(pageable);
        return queryRepository.audit(
            companyId, range.from(), range.to(), action, entityType, pageable
        );
    }

    private Page<CurrentAccountBalanceRow> balances(
        UUID companyId,
        boolean receivables,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        DateRange range = range(dateFrom, dateTo);
        validate(pageable);
        return queryRepository.currentAccountBalances(
            companyId, receivables, range.from(), range.to(), today(companyId), pageable
        );
    }

    private Page<SettlementMovementReportRow> settlementMovements(
        UUID companyId,
        boolean cash,
        UUID accountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable,
        Locale locale
    ) {
        DateRange range = range(dateFrom, dateTo);
        validate(pageable);
        return queryRepository.settlementMovements(
            companyId, cash, accountId, range.from(), range.to(), pageable, locale
        );
    }

    private Page<SettlementMovementReportRow> settlementMovements(
        UUID companyId,
        boolean cash,
        UUID accountId,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        DateRange range = range(dateFrom, dateTo);
        validate(pageable);
        return queryRepository.settlementMovements(
            companyId, cash, accountId, range.from(), range.to(), pageable
        );
    }

    private Page<InvoiceReportRow> invoices(
        UUID companyId,
        String type,
        LocalDate dateFrom,
        LocalDate dateTo,
        String status,
        Pageable pageable
    ) {
        DateRange range = range(dateFrom, dateTo);
        validateValue(status, INVOICE_STATUSES, "status");
        validate(pageable);
        return queryRepository.invoices(
            companyId, type, range.from(), range.to(), status, pageable
        );
    }

    private DateRange range(LocalDate dateFrom, LocalDate dateTo) {
        LocalDate from = dateFrom == null ? MIN_DATE : dateFrom;
        LocalDate to = dateTo == null ? MAX_DATE : dateTo;
        if (from.isBefore(MIN_DATE) || to.isAfter(MAX_DATE) || from.isAfter(to)) {
            throw invalid("Report date range is invalid.");
        }
        return new DateRange(from, to);
    }

    private void validate(Pageable pageable) {
        if (pageable.isUnpaged() || pageable.getPageSize() < 1 || pageable.getPageSize() > 100) {
            throw invalid("Report page size must be between 1 and 100.");
        }
    }

    private void validateValue(String value, Set<String> allowed, String field) {
        if (value != null && !allowed.contains(value)) {
            throw invalid(field + " is invalid.");
        }
    }

    private void validateText(String value, String field) {
        if (value != null && (value.isBlank() || value.length() > 80)) {
            throw invalid(field + " is invalid.");
        }
    }

    private LocalDate today(UUID companyId) {
        CompanyFinancialContext context = companyService.financialContext(companyId);
        return LocalDate.now(ZoneId.of(context.timezone()));
    }

    private BusinessException invalid(String message) {
        return new BusinessException("REPORT_FILTER_INVALID", message, HttpStatus.BAD_REQUEST);
    }

    private record DateRange(LocalDate from, LocalDate to) {
    }
}
