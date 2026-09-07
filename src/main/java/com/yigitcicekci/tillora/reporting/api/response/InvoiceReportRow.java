package com.yigitcicekci.tillora.reporting.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceReportRow(
    UUID id,
    String invoiceNumber,
    LocalDate invoiceDate,
    LocalDate dueDate,
    UUID currentAccountId,
    String currentAccountName,
    String status,
    String currency,
    BigDecimal exchangeRate,
    BigDecimal subtotal,
    BigDecimal discountTotal,
    BigDecimal taxTotal,
    BigDecimal grandTotal
) {
}
