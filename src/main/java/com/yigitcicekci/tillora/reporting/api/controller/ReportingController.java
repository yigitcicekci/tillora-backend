package com.yigitcicekci.tillora.reporting.api.controller;

import com.yigitcicekci.tillora.reporting.api.response.AuditReportRow;
import com.yigitcicekci.tillora.reporting.api.response.CurrentAccountBalanceRow;
import com.yigitcicekci.tillora.reporting.api.response.CurrentAccountStatementRow;
import com.yigitcicekci.tillora.reporting.api.response.InvoiceReportRow;
import com.yigitcicekci.tillora.reporting.api.response.SettlementMovementReportRow;
import com.yigitcicekci.tillora.reporting.api.response.VoucherReportRow;
import com.yigitcicekci.tillora.reporting.application.service.ReportingService;
import com.yigitcicekci.tillora.shared.i18n.RequestLocaleResolver;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasAuthority('REPORT_VIEW')")
public class ReportingController {

    private final ReportingService reportingService;

    public ReportingController(ReportingService reportingService) {
        this.reportingService = reportingService;
    }

    @GetMapping("/current-account-statement")
    Page<CurrentAccountStatementRow> currentAccountStatement(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam UUID currentAccountId,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @PageableDefault(size = 20) Pageable pageable,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return reportingService.currentAccountStatement(
            principal.companyId(),
            currentAccountId,
            dateFrom,
            dateTo,
            pageable,
            RequestLocaleResolver.resolve(acceptLanguage)
        );
    }

    @GetMapping("/receivables")
    Page<CurrentAccountBalanceRow> receivables(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return reportingService.receivables(principal.companyId(), dateFrom, dateTo, pageable);
    }

    @GetMapping("/payables")
    Page<CurrentAccountBalanceRow> payables(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return reportingService.payables(principal.companyId(), dateFrom, dateTo, pageable);
    }

    @GetMapping("/vouchers")
    Page<VoucherReportRow> vouchers(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @RequestParam(required = false) String voucherType,
        @RequestParam(required = false) String status,
        @PageableDefault(size = 20) Pageable pageable,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return reportingService.vouchers(
            principal.companyId(),
            dateFrom,
            dateTo,
            voucherType,
            status,
            pageable,
            RequestLocaleResolver.resolve(acceptLanguage)
        );
    }

    @GetMapping("/cash-movements")
    Page<SettlementMovementReportRow> cashMovements(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) UUID accountId,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @PageableDefault(size = 20) Pageable pageable,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return reportingService.cashMovements(
            principal.companyId(),
            accountId,
            dateFrom,
            dateTo,
            pageable,
            RequestLocaleResolver.resolve(acceptLanguage)
        );
    }

    @GetMapping("/bank-movements")
    Page<SettlementMovementReportRow> bankMovements(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) UUID accountId,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @PageableDefault(size = 20) Pageable pageable,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage
    ) {
        return reportingService.bankMovements(
            principal.companyId(),
            accountId,
            dateFrom,
            dateTo,
            pageable,
            RequestLocaleResolver.resolve(acceptLanguage)
        );
    }

    @GetMapping("/sales")
    Page<InvoiceReportRow> sales(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @RequestParam(required = false) String status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return reportingService.sales(principal.companyId(), dateFrom, dateTo, status, pageable);
    }

    @GetMapping("/purchases")
    Page<InvoiceReportRow> purchases(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @RequestParam(required = false) String status,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return reportingService.purchases(principal.companyId(), dateFrom, dateTo, status, pageable);
    }

    @GetMapping("/audit")
    Page<AuditReportRow> audit(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String entityType,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return reportingService.audit(
            principal.companyId(), dateFrom, dateTo, action, entityType, pageable
        );
    }
}
