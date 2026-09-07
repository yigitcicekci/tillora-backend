package com.yigitcicekci.tillora.invoice.api.response;

import com.yigitcicekci.tillora.invoice.domain.entity.Invoice;
import com.yigitcicekci.tillora.invoice.domain.entity.InvoiceLine;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoicePaymentStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    String invoiceNumber,
    InvoiceType invoiceType,
    UUID currentAccountId,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String currency,
    BigDecimal exchangeRate,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal grandTotal,
    BigDecimal costTotal,
    InvoiceStatus status,
    BigDecimal paidAmount,
    BigDecimal remainingAmount,
    InvoicePaymentStatus paymentStatus,
    UUID accountingVoucherId,
    UUID createdBy,
    UUID approvedBy,
    Instant createdAt,
    Instant updatedAt,
    Instant approvedAt,
    List<InvoiceLineResponse> lines
) {

    public static InvoiceResponse from(Invoice invoice, List<InvoiceLine> lines) {
        return from(invoice, lines, BigDecimal.ZERO.setScale(4));
    }

    public static InvoiceResponse from(
        Invoice invoice,
        List<InvoiceLine> lines,
        BigDecimal paidAmount
    ) {
        BigDecimal normalizedPaidAmount = paidAmount.setScale(4);
        BigDecimal remainingAmount = invoice.grandTotal().subtract(normalizedPaidAmount);
        InvoicePaymentStatus paymentStatus = normalizedPaidAmount.signum() == 0
            ? InvoicePaymentStatus.UNPAID
            : remainingAmount.signum() == 0
                ? InvoicePaymentStatus.PAID
                : InvoicePaymentStatus.PARTIALLY_PAID;
        return new InvoiceResponse(
            invoice.id(),
            invoice.invoiceNumber(),
            invoice.invoiceType(),
            invoice.currentAccountId(),
            invoice.invoiceDate(),
            invoice.dueDate(),
            invoice.currency(),
            invoice.exchangeRate(),
            invoice.subtotal(),
            invoice.discountTotal(),
            invoice.taxTotal(),
            invoice.grandTotal(),
            invoice.costTotal(),
            invoice.status(),
            normalizedPaidAmount,
            remainingAmount,
            paymentStatus,
            invoice.accountingVoucherId(),
            invoice.createdBy(),
            invoice.approvedBy(),
            invoice.createdAt(),
            invoice.updatedAt(),
            invoice.approvedAt(),
            lines.stream().map(InvoiceLineResponse::from).toList()
        );
    }
}
