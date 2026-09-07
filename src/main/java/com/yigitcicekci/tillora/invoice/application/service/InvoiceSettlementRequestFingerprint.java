package com.yigitcicekci.tillora.invoice.application.service;

import com.yigitcicekci.tillora.invoice.api.request.CreateInvoiceSettlementRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

final class InvoiceSettlementRequestFingerprint {

    private InvoiceSettlementRequestFingerprint() {
    }

    static String create(UUID invoiceId, CreateInvoiceSettlementRequest request) {
        String canonical = String.join(
            "|",
            invoiceId.toString(),
            request.settlementAccountType().name(),
            request.settlementAccountId().toString(),
            request.amount().toPlainString(),
            value(request.voucherDate()),
            value(request.movementNote()),
            value(request.documentNumber())
        );
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
