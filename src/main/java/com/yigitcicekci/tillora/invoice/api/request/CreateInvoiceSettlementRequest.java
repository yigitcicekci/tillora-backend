package com.yigitcicekci.tillora.invoice.api.request;

import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceSettlementAccountType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CreateInvoiceSettlementRequest(
    @NotNull UUID idempotencyKey,
    @NotNull InvoiceSettlementAccountType settlementAccountType,
    @NotNull UUID settlementAccountId,
    @NotNull @DecimalMin("0.0001") @Digits(integer = 15, fraction = 4) BigDecimal amount,
    LocalDate voucherDate,
    @Size(max = 500) String movementNote,
    @Size(max = 80) String documentNumber
) {
}
