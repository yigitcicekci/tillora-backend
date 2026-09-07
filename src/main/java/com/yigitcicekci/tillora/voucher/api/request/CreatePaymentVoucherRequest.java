package com.yigitcicekci.tillora.voucher.api.request;

import com.yigitcicekci.tillora.voucher.domain.enumeration.SettlementAccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreatePaymentVoucherRequest(
    @NotNull UUID idempotencyKey,
    @NotNull UUID currentAccountId,
    @NotNull SettlementAccountType settlementAccountType,
    @NotNull UUID settlementAccountId,
    @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
    LocalDate voucherDate,
    @Size(max = 500) String movementNote,
    @Size(max = 80) String documentNumber
) {
}
