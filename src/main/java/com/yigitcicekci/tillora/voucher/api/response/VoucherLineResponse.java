package com.yigitcicekci.tillora.voucher.api.response;

import com.yigitcicekci.tillora.voucher.domain.entity.VoucherLine;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VoucherLineResponse(
    UUID id,
    int lineNumber,
    UUID chartOfAccountId,
    UUID currentAccountId,
    String accountCode,
    String accountName,
    String movementNote,
    BigDecimal debit,
    BigDecimal credit,
    BigDecimal quantity,
    LocalDate dueDate,
    BigDecimal currencyAmount
) {
    public static VoucherLineResponse from(VoucherLine line) {
        return new VoucherLineResponse(
            line.id(),
            line.lineNumber(),
            line.chartOfAccountId(),
            line.currentAccountId(),
            line.accountCode(),
            line.accountName(),
            line.movementNote(),
            line.debit(),
            line.credit(),
            line.quantity(),
            line.dueDate(),
            line.currencyAmount()
        );
    }
}
