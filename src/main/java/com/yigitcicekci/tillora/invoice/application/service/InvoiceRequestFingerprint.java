package com.yigitcicekci.tillora.invoice.application.service;

import com.yigitcicekci.tillora.invoice.api.request.InvoiceLineRequest;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

final class InvoiceRequestFingerprint {

    private InvoiceRequestFingerprint() {
    }

    static String create(
        InvoiceType invoiceType,
        UUID currentAccountId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        List<InvoiceLineRequest> lines
    ) {
        String lineValues = lines.stream()
            .map(line -> String.join(
                ":",
                line.productId().toString(),
                value(line.description()),
                line.quantity().toPlainString(),
                line.unitPrice().toPlainString(),
                line.discountRate().toPlainString(),
                line.vatRate().toPlainString()
            ))
            .collect(Collectors.joining(";"));
        String canonical = String.join(
            "|",
            invoiceType.name(),
            currentAccountId.toString(),
            value(invoiceDate),
            value(dueDate),
            value(currency),
            lineValues
        );
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }
}
