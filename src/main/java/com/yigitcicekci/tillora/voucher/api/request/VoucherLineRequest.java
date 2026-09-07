package com.yigitcicekci.tillora.voucher.api.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record VoucherLineRequest(
    @NotNull UUID chartOfAccountId,
    @Size(max = 500) String movementNote,
    @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal debit,
    @NotNull @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal credit,
    @DecimalMin(value = "0.000001") @Digits(integer = 13, fraction = 6) BigDecimal quantity,
    LocalDate dueDate
) {
}
