package com.yigitcicekci.tillora.reporting.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record SettlementMovementReportRow(
    UUID accountId,
    String accountName,
    String accountCode,
    UUID voucherId,
    String voucherNumber,
    String voucherType,
    LocalDate voucherDate,
    String movementNote,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal balanceChange,
    String currency,
    BigDecimal exchangeRate
) {
}
