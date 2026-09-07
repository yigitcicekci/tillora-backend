package com.yigitcicekci.tillora.reporting.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CurrentAccountStatementRow(
    UUID voucherId,
    String voucherNumber,
    String voucherType,
    LocalDate voucherDate,
    String documentNumber,
    String movementNote,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal balance,
    String currency,
    BigDecimal exchangeRate
) {
}
