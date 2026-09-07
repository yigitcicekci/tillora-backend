package com.yigitcicekci.tillora.audit.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "audit_logs")
public class AuditLog {

    @Id
    private UUID id;

    private UUID companyId;

    private UUID userId;

    private UUID platformAdminId;

    @Column(nullable = false, length = 80)
    private String action;

    @Column(nullable = false, length = 80)
    private String entityType;

    private UUID entityId;

    @Column(length = 24)
    private String result;

    @Column(length = 80)
    private String correlationId;

    @Column(length = 64)
    private String ipAddress;

    @Column(length = 512)
    private String userAgent;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> beforeData;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, String> afterData;

    @Column(nullable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    private AuditLog(
        UUID companyId,
        UUID userId,
        UUID platformAdminId,
        String action,
        String entityType,
        UUID entityId,
        String result,
        String correlationId,
        String ipAddress,
        String userAgent,
        Map<String, String> beforeData,
        Map<String, String> afterData
    ) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.userId = userId;
        this.platformAdminId = platformAdminId;
        this.action = action;
        this.entityType = entityType;
        this.entityId = entityId;
        this.result = truncate(result, 24);
        this.correlationId = truncate(correlationId, 80);
        this.ipAddress = truncate(ipAddress, 64);
        this.userAgent = truncate(userAgent, 512);
        this.beforeData = nullable(beforeData);
        this.afterData = nullable(afterData);
        this.createdAt = Instant.now();
    }

    public static AuditLog create(
        UUID companyId,
        UUID userId,
        String action,
        String entityType,
        UUID entityId,
        String correlationId,
        String ipAddress,
        String userAgent,
        Map<String, String> beforeData,
        Map<String, String> afterData
    ) {
        return new AuditLog(
            companyId,
            userId,
            null,
            action,
            entityType,
            entityId,
            null,
            correlationId,
            ipAddress,
            userAgent,
            beforeData,
            afterData
        );
    }

    public static AuditLog createPlatformAdmin(
        UUID companyId,
        UUID platformAdminId,
        String action,
        String entityType,
        UUID entityId,
        String result,
        String correlationId,
        String ipAddress,
        String userAgent,
        Map<String, String> beforeData,
        Map<String, String> afterData
    ) {
        return new AuditLog(
            companyId,
            null,
            platformAdminId,
            action,
            entityType,
            entityId,
            result,
            correlationId,
            ipAddress,
            userAgent,
            beforeData,
            afterData
        );
    }

    private static String truncate(String value, int maxLength) {
        return value == null || value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static Map<String, String> nullable(Map<String, String> values) {
        return values == null || values.isEmpty() ? null : Map.copyOf(values);
    }
}
