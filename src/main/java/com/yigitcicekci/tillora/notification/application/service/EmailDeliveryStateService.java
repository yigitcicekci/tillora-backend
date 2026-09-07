package com.yigitcicekci.tillora.notification.application.service;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditDetails;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.notification.domain.entity.EmailDelivery;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailAttachmentType;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryReferenceType;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import com.yigitcicekci.tillora.notification.domain.repository.EmailDeliveryRepository;
import com.yigitcicekci.tillora.notification.infrastructure.persistence.EmailDeliveryLockRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmailDeliveryStateService {

    private final EmailDeliveryRepository repository;
    private final EmailDeliveryLockRepository lockRepository;
    private final AuditLogService auditLogService;

    public EmailDeliveryStateService(
        EmailDeliveryRepository repository,
        EmailDeliveryLockRepository lockRepository,
        AuditLogService auditLogService
    ) {
        this.repository = repository;
        this.lockRepository = lockRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PreparedEmailDelivery prepareCurrentAccountStatement(
        UUID companyId,
        UUID actorUserId,
        UUID currentAccountId,
        String idempotencyKey,
        String fingerprint,
        String subject,
        List<String> to,
        List<String> cc,
        List<EmailAttachmentType> attachmentTypes
    ) {
        lockRepository.acquire(
            companyId,
            EmailDeliveryReferenceType.CURRENT_ACCOUNT_STATEMENT,
            currentAccountId,
            idempotencyKey
        );
        EmailDelivery existing = repository
            .findByCompanyIdAndReferenceTypeAndReferenceIdAndIdempotencyKey(
                companyId,
                EmailDeliveryReferenceType.CURRENT_ACCOUNT_STATEMENT,
                currentAccountId,
                idempotencyKey
            )
            .orElse(null);
        if (existing != null) {
            if (!existing.requestFingerprint().equals(fingerprint)) {
                throw new BusinessException(
                    "IDEMPOTENCY_KEY_REUSED",
                    "Idempotency key was already used with different content.",
                    HttpStatus.CONFLICT
                );
            }
            return new PreparedEmailDelivery(existing, false);
        }
        EmailDelivery delivery = repository.saveAndFlush(EmailDelivery.pendingCurrentAccountStatement(
            companyId,
            currentAccountId,
            EmailProviderType.BREVO,
            subject,
            to,
            cc,
            attachmentTypes,
            idempotencyKey,
            fingerprint,
            actorUserId
        ));
        auditLogService.record(
            companyId,
            actorUserId,
            AuditAction.EMAIL_DELIVERY_REQUESTED,
            "EMAIL_DELIVERY",
            delivery.id(),
            details(delivery, "PENDING")
        );
        return new PreparedEmailDelivery(delivery, true);
    }

    @Transactional
    public EmailDelivery sent(UUID companyId, UUID deliveryId, String providerMessageId) {
        EmailDelivery delivery = find(companyId, deliveryId);
        delivery.markSent(providerMessageId);
        delivery = repository.saveAndFlush(delivery);
        auditLogService.record(
            companyId,
            delivery.createdBy(),
            AuditAction.EMAIL_DELIVERY_SENT,
            "EMAIL_DELIVERY",
            delivery.id(),
            details(delivery, "SENT")
        );
        return delivery;
    }

    @Transactional
    public EmailDelivery failed(
        UUID companyId,
        UUID deliveryId,
        String code,
        String message
    ) {
        EmailDelivery delivery = find(companyId, deliveryId);
        delivery.markFailed(code, message);
        delivery = repository.saveAndFlush(delivery);
        auditLogService.record(
            companyId,
            delivery.createdBy(),
            AuditAction.EMAIL_DELIVERY_FAILED,
            "EMAIL_DELIVERY",
            delivery.id(),
            details(delivery, "FAILED")
        );
        return delivery;
    }

    private EmailDelivery find(UUID companyId, UUID deliveryId) {
        return repository.findByIdAndCompanyId(deliveryId, companyId)
            .orElseThrow(() -> new BusinessException(
                "EMAIL_DELIVERY_NOT_FOUND",
                "Email delivery not found.",
                HttpStatus.NOT_FOUND
            ));
    }

    private AuditDetails details(EmailDelivery delivery, String status) {
        Map<String, String> after = new java.util.LinkedHashMap<>();
        after.put("referenceType", delivery.referenceType().name());
        after.put("referenceId", delivery.referenceId().toString());
        after.put("deliveryId", delivery.id().toString());
        after.put(
            "recipientCount",
            Integer.toString(delivery.toRecipients().size() + delivery.ccRecipients().size())
        );
        after.put("attachmentTypes", String.join(",", delivery.attachmentTypes()));
        after.put("provider", delivery.provider().name());
        after.put("status", status);
        return new AuditDetails(Map.of(), after);
    }
}
