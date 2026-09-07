package com.yigitcicekci.tillora.voucher.api.request;

import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferSourceAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferTargetAccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateTransferVoucherRequest(
    @NotNull UUID idempotencyKey,
    @NotNull TransferSourceAccountType sourceAccountType,
    @NotNull UUID sourceAccountId,
    @NotNull TransferTargetAccountType targetAccountType,
    @NotNull UUID targetAccountId,
    @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
    LocalDate voucherDate,
    @Size(max = 500) String movementNote,
    @Size(max = 80) String documentNumber
) {
}
