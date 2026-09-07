package com.yigitcicekci.tillora.voucher.api.response;

import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record VoucherSummaryResponse(
    UUID id,
    String voucherNumber,
    VoucherType voucherType,
    LocalDate voucherDate,
    String movementNote,
    String documentNumber,
    String currency,
    BigDecimal exchangeRate,
    BigDecimal totalDebit,
    BigDecimal totalCredit,
    VoucherStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static VoucherSummaryResponse from(Voucher voucher) {
        return new VoucherSummaryResponse(
            voucher.id(),
            voucher.voucherNumber(),
            voucher.voucherType(),
            voucher.voucherDate(),
            voucher.movementNote(),
            voucher.documentNumber(),
            voucher.currency(),
            voucher.exchangeRate(),
            voucher.totalDebit(),
            voucher.totalCredit(),
            voucher.status(),
            voucher.createdAt(),
            voucher.updatedAt()
        );
    }
}
