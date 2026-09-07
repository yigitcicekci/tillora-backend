package com.yigitcicekci.tillora.voucher.application.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSettlementVoucherRequest(
    UUID idempotencyKey,
    UUID currentAccountId,
    SettlementAccountReferenceType settlementAccountType,
    UUID settlementAccountId,
    BigDecimal amount,
    LocalDate voucherDate,
    String movementNote,
    String documentNumber
) {
}
