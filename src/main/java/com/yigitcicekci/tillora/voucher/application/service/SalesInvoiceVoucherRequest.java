package com.yigitcicekci.tillora.voucher.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SalesInvoiceVoucherRequest(
    UUID invoiceId,
    String invoiceNumber,
    UUID currentAccountId,
    UUID currentAccountChartAccountId,
    LocalDate invoiceDate,
    LocalDate dueDate,
    String currency,
    BigDecimal netSales,
    BigDecimal taxTotal,
    BigDecimal grandTotal,
    BigDecimal costTotal
) {
}
