package com.yigitcicekci.tillora.audit.api.response;

import java.time.Instant;
import java.util.UUID;

public record PlatformAdminAuditLogResponse(
    UUID id,
    UUID platformAdminId,
    String action,
    String entityType,
    UUID entityId,
    String result,
    String correlationId,
    String ipAddress,
    String userAgent,
    Instant createdAt
) {
}
