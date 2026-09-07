package com.yigitcicekci.tillora.reporting.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VoucherReportRow(
    UUID id,
    String voucherNumber,
    String voucherType,
    LocalDate voucherDate,
    String status,
    String documentNumber,
    String movementNote,
    String currency,
    BigDecimal exchangeRate,
    BigDecimal totalDebit,
    BigDecimal totalCredit
) {
}
