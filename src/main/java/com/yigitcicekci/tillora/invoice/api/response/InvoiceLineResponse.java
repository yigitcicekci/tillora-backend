package com.yigitcicekci.tillora.invoice.api.response;

import com.yigitcicekci.tillora.invoice.domain.entity.InvoiceLine;
import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineResponse(
    UUID id,
    int lineNumber,
    UUID productId,
    String productCode,
    String description,
    BigDecimal quantity,
    String unit,
    BigDecimal unitPrice,
    BigDecimal discountRate,
    BigDecimal discountAmount,
    BigDecimal vatRate,
    BigDecimal vatAmount,
    BigDecimal lineTotal,
    BigDecimal unitCost,
    BigDecimal costTotal
) {

    public static InvoiceLineResponse from(InvoiceLine line) {
        return new InvoiceLineResponse(
            line.id(),
            line.lineNumber(),
            line.productId(),
            line.productCode(),
            line.description(),
            line.quantity(),
            line.unit(),
            line.unitPrice(),
            line.discountRate(),
            line.discountAmount(),
            line.vatRate(),
            line.vatAmount(),
            line.lineTotal(),
            line.unitCost(),
            line.costTotal()
        );
    }
}
