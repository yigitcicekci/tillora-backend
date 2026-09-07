package com.yigitcicekci.tillora.reporting.api.response;

import java.time.Instant;
import java.util.UUID;

public record AuditReportRow(
    UUID id,
    UUID userId,
    String username,
    String action,
    String entityType,
    UUID entityId,
    String correlationId,
    String ipAddress,
    Instant createdAt
) {
}
