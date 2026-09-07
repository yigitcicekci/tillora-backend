package com.yigitcicekci.tillora.notification.application.service;

import com.yigitcicekci.tillora.notification.domain.enumeration.EmailAttachmentType;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

final class EmailRequestFingerprint {

    private EmailRequestFingerprint() {
    }

    static String create(
        List<String> to,
        List<String> cc,
        String subject,
        String message,
        List<EmailAttachmentType> attachments
    ) {
        return hash(canonical(to, cc, subject, message, attachments));
    }

    static String create(
        List<String> to,
        List<String> cc,
        String subject,
        String message,
        List<EmailAttachmentType> attachments,
        String context
    ) {
        return hash(canonical(to, cc, subject, message, attachments) + value(context));
    }

    private static String canonical(
        List<String> to,
        List<String> cc,
        String subject,
        String message,
        List<EmailAttachmentType> attachments
    ) {
        return sequence(to.stream().sorted().toList())
            + sequence(cc.stream().sorted().toList())
            + value(subject)
            + value(message)
            + sequence(attachments.stream().map(Enum::name).sorted().toList());
    }

    private static String hash(String canonical) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private static String sequence(List<String> values) {
        return values.size() + ":" + values.stream()
            .map(EmailRequestFingerprint::value)
            .collect(java.util.stream.Collectors.joining());
    }

    private static String value(String value) {
        return value.length() + ":" + value;
    }
}
