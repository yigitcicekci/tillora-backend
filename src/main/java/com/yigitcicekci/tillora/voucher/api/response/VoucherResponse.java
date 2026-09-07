package com.yigitcicekci.tillora.voucher.api.response;

import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
import com.yigitcicekci.tillora.voucher.domain.entity.VoucherLine;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record VoucherResponse(
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
    UUID createdBy,
    UUID idempotencyKey,
    UUID approvedBy,
    UUID cancelledBy,
    String cancellationReason,
    Instant createdAt,
    Instant updatedAt,
    Instant approvedAt,
    Instant cancelledAt,
    List<VoucherLineResponse> lines
) {
    public static VoucherResponse from(Voucher voucher, List<VoucherLine> lines) {
        return new VoucherResponse(
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
            voucher.createdBy(),
            voucher.idempotencyKey(),
            voucher.approvedBy(),
            voucher.cancelledBy(),
            voucher.cancellationReason(),
            voucher.createdAt(),
            voucher.updatedAt(),
            voucher.approvedAt(),
            voucher.cancelledAt(),
            lines.stream().map(VoucherLineResponse::from).toList()
        );
    }
}
