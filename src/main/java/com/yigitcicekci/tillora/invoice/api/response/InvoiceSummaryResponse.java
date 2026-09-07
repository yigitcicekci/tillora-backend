package com.yigitcicekci.tillora.invoice.api.response;

import com.yigitcicekci.tillora.invoice.domain.entity.Invoice;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummaryResponse(
    UUID id,
    String invoiceNumber,
    InvoiceType invoiceType,
    UUID currentAccountId,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String currency,
    BigDecimal grandTotal,
    InvoiceStatus status
) {

    public static InvoiceSummaryResponse from(Invoice invoice) {
        return new InvoiceSummaryResponse(
            invoice.id(),
            invoice.invoiceNumber(),
            invoice.invoiceType(),
            invoice.currentAccountId(),
            invoice.invoiceDate(),
            invoice.dueDate(),
            invoice.currency(),
            invoice.grandTotal(),
            invoice.status()
        );
    }
}
