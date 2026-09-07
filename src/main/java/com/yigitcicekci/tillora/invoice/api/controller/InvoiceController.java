package com.yigitcicekci.tillora.invoice.api.controller;

import com.yigitcicekci.tillora.invoice.api.request.CreatePurchaseInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.request.CreateSalesInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.request.CreateInvoiceSettlementRequest;
import com.yigitcicekci.tillora.invoice.api.request.UpdateInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.response.InvoiceResponse;
import com.yigitcicekci.tillora.invoice.api.response.InvoiceSummaryResponse;
import com.yigitcicekci.tillora.invoice.application.service.InvoiceService;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    @PostMapping("/sales")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    ResponseEntity<InvoiceResponse> createSales(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreateSalesInvoiceRequest request
    ) {
        InvoiceResponse response = invoiceService.createSales(
            principal.companyId(),
            principal.userId(),
            request
        );
        return ResponseEntity.created(URI.create("/api/v1/invoices/" + response.id())).body(response);
    }

    @PostMapping("/purchases")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    ResponseEntity<InvoiceResponse> createPurchase(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreatePurchaseInvoiceRequest request
    ) {
        InvoiceResponse response = invoiceService.createPurchase(
            principal.companyId(),
            principal.userId(),
            request
        );
        return ResponseEntity.created(URI.create("/api/v1/invoices/" + response.id())).body(response);
    }

    @PutMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('INVOICE_CREATE')")
    InvoiceResponse update(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID invoiceId,
        @Valid @RequestBody UpdateInvoiceRequest request
    ) {
        return invoiceService.update(
            principal.companyId(),
            principal.userId(),
            invoiceId,
            request
        );
    }

    @GetMapping
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    Page<InvoiceSummaryResponse> list(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) InvoiceType invoiceType,
        @RequestParam(required = false) InvoiceStatus status,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        Pageable pageable
    ) {
        return invoiceService.list(
            principal.companyId(),
            invoiceType,
            status,
            dateFrom,
            dateTo,
            pageable
        );
    }

    @GetMapping("/{invoiceId}")
    @PreAuthorize("hasAuthority('INVOICE_READ')")
    InvoiceResponse get(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID invoiceId
    ) {
        return invoiceService.get(principal.companyId(), invoiceId);
    }

    @PostMapping("/{invoiceId}/approve")
    @PreAuthorize("hasAuthority('INVOICE_APPROVE')")
    InvoiceResponse approve(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID invoiceId
    ) {
        return invoiceService.approve(
            principal.companyId(),
            principal.userId(),
            invoiceId
        );
    }

    @PostMapping("/{invoiceId}/approve-with-settlement")
    @PreAuthorize("hasAuthority('INVOICE_READ') and hasAuthority('INVOICE_APPROVE') and hasAuthority('VOUCHER_CREATE') and hasAuthority('VOUCHER_APPROVE')")
    InvoiceResponse approveWithSettlement(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID invoiceId,
        @Valid @RequestBody CreateInvoiceSettlementRequest request
    ) {
        return invoiceService.approveWithSettlement(
            principal.companyId(),
            principal.userId(),
            invoiceId,
            request
        );
    }

    @PostMapping("/{invoiceId}/settlements")
    @PreAuthorize("hasAuthority('INVOICE_READ') and hasAuthority('VOUCHER_CREATE') and hasAuthority('VOUCHER_APPROVE')")
    InvoiceResponse settle(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID invoiceId,
        @Valid @RequestBody CreateInvoiceSettlementRequest request
    ) {
        return invoiceService.settle(
            principal.companyId(),
            principal.userId(),
            invoiceId,
            request
        );
    }
}
