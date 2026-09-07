package com.yigitcicekci.tillora.audit.api.response;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID userId,
    String username,
    String action,
    String entityType,
    UUID entityId,
    String correlationId,
    String ipAddress,
    String userAgent,
    Map<String, String> beforeData,
    Map<String, String> afterData,
    Instant createdAt
) {
}
