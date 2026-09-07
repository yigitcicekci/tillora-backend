package com.yigitcicekci.tillora.audit.application.service;

import java.util.Map;

public record AuditDetails(Map<String, String> before, Map<String, String> after) {

    public AuditDetails {
        before = immutable(before);
        after = immutable(after);
    }

    public static AuditDetails empty() {
        return new AuditDetails(Map.of(), Map.of());
    }

    public static AuditDetails transition(String field, String before, String after) {
        return new AuditDetails(Map.of(field, before), Map.of(field, after));
    }

    public static AuditDetails created() {
        return new AuditDetails(Map.of(), Map.of("lifecycle", "CREATED"));
    }

    private static Map<String, String> immutable(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        values.forEach(AuditDetails::validate);
        return Map.copyOf(values);
    }

    private static void validate(String key, String value) {
        if (key == null || key.isBlank() || key.length() > 80
            || value == null || value.length() > 500
            || sensitive(key)) {
            throw new IllegalArgumentException("Audit details are invalid.");
        }
    }

    private static boolean sensitive(String key) {
        String normalized = key.toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("password")
            || normalized.contains("secret")
            || normalized.contains("token")
            || normalized.contains("credential")
            || normalized.contains("sms")
            || normalized.contains("taxnumber")
            || normalized.contains("identitynumber")
            || normalized.contains("iban")
            || normalized.contains("email")
            || normalized.contains("phone")
            || normalized.contains("address")
            || normalized.contains("xml")
            || normalized.contains("content");
    }
}
