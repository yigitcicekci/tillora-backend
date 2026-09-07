package com.yigitcicekci.tillora.reporting.application.service;

import com.yigitcicekci.tillora.reporting.api.response.CurrentAccountStatementRow;
import java.math.BigDecimal;
import java.time.LocalDate;

public record CurrentAccountStatementExportRow(
    LocalDate voucherDate,
    String voucherNumber,
    String voucherType,
    String documentNumber,
    String movementNote,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal balance,
    String currency,
    BigDecimal exchangeRate
) {

    public static CurrentAccountStatementExportRow from(CurrentAccountStatementRow row) {
        return new CurrentAccountStatementExportRow(
            row.voucherDate(),
            row.voucherNumber(),
            row.voucherType(),
            row.documentNumber(),
            row.movementNote(),
            row.debit(),
            row.credit(),
            row.balance(),
            row.currency(),
            row.exchangeRate()
        );
    }
}
