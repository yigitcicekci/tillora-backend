package com.yigitcicekci.tillora.voucher.application.service;

import com.yigitcicekci.tillora.voucher.domain.enumeration.SettlementAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferSourceAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferTargetAccountType;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;

final class GuidedVoucherRequestFingerprint {

    private GuidedVoucherRequestFingerprint() {
    }

    static String create(
        UUID currentAccountId,
        SettlementAccountType settlementAccountType,
        UUID settlementAccountId,
        BigDecimal amount,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber
    ) {
        String canonical = String.join(
            "|",
            currentAccountId.toString(),
            settlementAccountType.name(),
            settlementAccountId.toString(),
            amount.toPlainString(),
            value(voucherDate),
            value(movementNote),
            value(documentNumber)
        );
        return hash(canonical);
    }

    static String transfer(
        TransferSourceAccountType sourceAccountType,
        UUID sourceAccountId,
        TransferTargetAccountType targetAccountType,
        UUID targetAccountId,
        BigDecimal amount,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber
    ) {
        String canonical = String.join(
            "|",
            "TRANSFER",
            sourceAccountType.name(),
            sourceAccountId.toString(),
            targetAccountType.name(),
            targetAccountId.toString(),
            amount.toPlainString(),
            value(voucherDate),
            value(movementNote),
            value(documentNumber)
        );
        return hash(canonical);
    }

    private static String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
