package com.yigitcicekci.tillora.notification.domain.entity;

import com.yigitcicekci.tillora.notification.domain.enumeration.EmailAttachmentType;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryStatus;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "email_deliveries")
public class EmailDelivery {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailDeliveryReferenceType referenceType;

    @Column(nullable = false)
    private UUID referenceId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EmailProviderType provider;

    @Column(length = 255)
    private String providerMessageId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private EmailDeliveryStatus status;

    @Column(nullable = false, length = 200)
    private String subject;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> toRecipients;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> ccRecipients;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private List<String> attachmentTypes;

    @Column(nullable = false, length = 128)
    private String idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @Column(length = 64)
    private String failureCode;

    @Column(length = 500)
    private String failureMessage;

    @Column(nullable = false)
    private UUID createdBy;

    @Column(nullable = false)
    private Instant createdAt;

    private Instant sentAt;

    private Instant failedAt;

    @Version
    private long version;

    protected EmailDelivery() {
    }

    public static EmailDelivery pendingCurrentAccountStatement(
        UUID companyId,
        UUID currentAccountId,
        EmailProviderType provider,
        String subject,
        List<String> toRecipients,
        List<String> ccRecipients,
        List<EmailAttachmentType> attachmentTypes,
        String idempotencyKey,
        String requestFingerprint,
        UUID createdBy
    ) {
        EmailDelivery delivery = new EmailDelivery();
        delivery.id = UUID.randomUUID();
        delivery.companyId = companyId;
        delivery.referenceType = EmailDeliveryReferenceType.CURRENT_ACCOUNT_STATEMENT;
        delivery.referenceId = currentAccountId;
        delivery.provider = provider;
        delivery.status = EmailDeliveryStatus.PENDING;
        delivery.subject = subject;
        delivery.toRecipients = List.copyOf(toRecipients);
        delivery.ccRecipients = List.copyOf(ccRecipients);
        delivery.attachmentTypes = attachmentTypes.stream().map(Enum::name).toList();
        delivery.idempotencyKey = idempotencyKey;
        delivery.requestFingerprint = requestFingerprint;
        delivery.createdBy = createdBy;
        delivery.createdAt = Instant.now();
        return delivery;
    }

    public void markSent(String messageId) {
        if (status != EmailDeliveryStatus.PENDING || messageId == null || messageId.isBlank()) {
            throw new IllegalStateException("Email delivery cannot be marked sent.");
        }
        providerMessageId = messageId.length() <= 255 ? messageId : messageId.substring(0, 255);
        status = EmailDeliveryStatus.SENT;
        sentAt = Instant.now();
    }

    public void markFailed(String code, String message) {
        if (status != EmailDeliveryStatus.PENDING) {
            return;
        }
        failureCode = safe(code, "EMAIL_PROVIDER_UNAVAILABLE", 64);
        failureMessage = safe(message, "Email provider is unavailable.", 500);
        status = EmailDeliveryStatus.FAILED;
        failedAt = Instant.now();
    }

    private String safe(String value, String fallback, int length) {
        value = value == null || value.isBlank() ? fallback : value;
        return value.length() <= length ? value : value.substring(0, length);
    }

    public UUID id() { return id; }
    public UUID companyId() { return companyId; }
    public EmailDeliveryReferenceType referenceType() { return referenceType; }
    public UUID referenceId() { return referenceId; }
    public EmailProviderType provider() { return provider; }
    public String providerMessageId() { return providerMessageId; }
    public EmailDeliveryStatus status() { return status; }
    public String subject() { return subject; }
    public List<String> toRecipients() { return List.copyOf(toRecipients); }
    public List<String> ccRecipients() { return List.copyOf(ccRecipients); }
    public List<String> attachmentTypes() { return List.copyOf(attachmentTypes); }
    public String idempotencyKey() { return idempotencyKey; }
    public String requestFingerprint() { return requestFingerprint; }
    public String failureCode() { return failureCode; }
    public String failureMessage() { return failureMessage; }
    public UUID createdBy() { return createdBy; }
    public Instant createdAt() { return createdAt; }
    public Instant sentAt() { return sentAt; }
    public Instant failedAt() { return failedAt; }
}
