package com.yigitcicekci.tillora.invoice.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.invoice.api.request.CreatePurchaseInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.request.CreateSalesInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.request.CreateInvoiceSettlementRequest;
import com.yigitcicekci.tillora.invoice.api.request.InvoiceLineRequest;
import com.yigitcicekci.tillora.invoice.api.request.UpdateInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.response.InvoiceResponse;
import com.yigitcicekci.tillora.invoice.application.service.InvoiceService;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoicePaymentStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceSettlementAccountType;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class InvoiceControllerTest {

    private final InvoiceService service = mock(InvoiceService.class);
    private final InvoiceController controller = new InvoiceController(service);

    @Test
    void usesAuthenticatedTenantAndActorForSalesInvoiceOperations() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        LocalDate invoiceDate = LocalDate.of(2026, 7, 17);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            actorId,
            companyId,
            "invoice",
            Set.of("INVOICE_READ", "INVOICE_CREATE", "INVOICE_APPROVE")
        );
        List<InvoiceLineRequest> lines = List.of(
            new InvoiceLineRequest(
                productId,
                "Product",
                new BigDecimal("2"),
                new BigDecimal("100"),
                new BigDecimal("10"),
                new BigDecimal("20")
            )
        );
        CreateSalesInvoiceRequest createRequest = new CreateSalesInvoiceRequest(
            UUID.randomUUID(),
            currentAccountId,
            invoiceDate,
            invoiceDate.plusDays(30),
            "TRY",
            lines
        );
        CreatePurchaseInvoiceRequest purchaseRequest = new CreatePurchaseInvoiceRequest(
            UUID.randomUUID(),
            currentAccountId,
            invoiceDate,
            invoiceDate.plusDays(30),
            "TRY",
            lines
        );
        UpdateInvoiceRequest updateRequest = new UpdateInvoiceRequest(
            currentAccountId,
            invoiceDate,
            invoiceDate.plusDays(45),
            "TRY",
            lines
        );
        CreateInvoiceSettlementRequest settlementRequest = new CreateInvoiceSettlementRequest(
            UUID.randomUUID(),
            InvoiceSettlementAccountType.CASH,
            UUID.randomUUID(),
            new BigDecimal("216.0000"),
            invoiceDate,
            "Cash sale",
            null
        );
        InvoiceResponse response = response(
            invoiceId,
            currentAccountId,
            actorId,
            invoiceDate
        );
        PageRequest pageable = PageRequest.of(0, 20);
        when(service.createSales(companyId, actorId, createRequest)).thenReturn(response);
        when(service.createPurchase(companyId, actorId, purchaseRequest)).thenReturn(response);

        var created = controller.createSales(principal, createRequest);
        controller.createPurchase(principal, purchaseRequest);
        controller.update(principal, invoiceId, updateRequest);
        controller.list(
            principal,
            InvoiceType.SALES,
            InvoiceStatus.DRAFT,
            invoiceDate,
            invoiceDate,
            pageable
        );
        controller.get(principal, invoiceId);
        controller.approve(principal, invoiceId);
        controller.approveWithSettlement(principal, invoiceId, settlementRequest);
        controller.settle(principal, invoiceId, settlementRequest);

        assertThat(created.getHeaders().getLocation())
            .isEqualTo(URI.create("/api/v1/invoices/" + invoiceId));
        assertThat(created.getBody()).isEqualTo(response);
        verify(service).createSales(companyId, actorId, createRequest);
        verify(service).createPurchase(companyId, actorId, purchaseRequest);
        verify(service).update(companyId, actorId, invoiceId, updateRequest);
        verify(service).list(
            companyId,
            InvoiceType.SALES,
            InvoiceStatus.DRAFT,
            invoiceDate,
            invoiceDate,
            pageable
        );
        verify(service).get(companyId, invoiceId);
        verify(service).approve(companyId, actorId, invoiceId);
        verify(service).approveWithSettlement(companyId, actorId, invoiceId, settlementRequest);
        verify(service).settle(companyId, actorId, invoiceId, settlementRequest);
    }

    private InvoiceResponse response(
        UUID invoiceId,
        UUID currentAccountId,
        UUID actorId,
        LocalDate invoiceDate
    ) {
        Instant now = Instant.now();
        return new InvoiceResponse(
            invoiceId,
            "SF-2026-000001",
            InvoiceType.SALES,
            currentAccountId,
            invoiceDate,
            invoiceDate.plusDays(30),
            "TRY",
            new BigDecimal("1.00000000"),
            new BigDecimal("200.0000"),
            new BigDecimal("20.0000"),
            new BigDecimal("36.0000"),
            new BigDecimal("216.0000"),
            new BigDecimal("100.0000"),
            InvoiceStatus.DRAFT,
            new BigDecimal("0.0000"),
            new BigDecimal("216.0000"),
            InvoicePaymentStatus.UNPAID,
            null,
            actorId,
            null,
            now,
            now,
            null,
            List.of()
        );
    }
}
