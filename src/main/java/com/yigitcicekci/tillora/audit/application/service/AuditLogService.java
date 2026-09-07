package com.yigitcicekci.tillora.audit.application.service;

import com.yigitcicekci.tillora.audit.domain.entity.AuditLog;
import com.yigitcicekci.tillora.audit.domain.repository.AuditLogRepository;
import com.yigitcicekci.tillora.shared.error.CorrelationIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public AuditLogService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional
    public void record(UUID companyId, UUID userId, AuditAction action, String entityType, UUID entityId) {
        auditLogRepository.save(create(companyId, userId, action, entityType, entityId, details(action)));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordRequiresNew(UUID companyId, UUID userId, AuditAction action, String entityType, UUID entityId) {
        auditLogRepository.save(create(companyId, userId, action, entityType, entityId, details(action)));
    }

    @Transactional
    public void record(
        UUID companyId,
        UUID userId,
        AuditAction action,
        String entityType,
        UUID entityId,
        AuditDetails details
    ) {
        auditLogRepository.save(create(companyId, userId, action, entityType, entityId, details));
    }

    @Transactional
    public void recordPlatformAdmin(
        UUID companyId,
        UUID platformAdminId,
        AuditAction action,
        String entityType,
        UUID entityId,
        String result
    ) {
        auditLogRepository.save(createPlatformAdmin(
            companyId,
            platformAdminId,
            action,
            entityType,
            entityId,
            result,
            AuditDetails.empty()
        ));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordPlatformAdminRequiresNew(
        UUID companyId,
        UUID platformAdminId,
        AuditAction action,
        String entityType,
        UUID entityId,
        String result
    ) {
        auditLogRepository.save(createPlatformAdmin(
            companyId,
            platformAdminId,
            action,
            entityType,
            entityId,
            result,
            AuditDetails.empty()
        ));
    }

    private AuditLog create(
        UUID companyId,
        UUID userId,
        AuditAction action,
        String entityType,
        UUID entityId,
        AuditDetails details
    ) {
        HttpServletRequest request = currentRequest();
        return AuditLog.create(
            companyId,
            userId,
            action.name(),
            entityType,
            entityId,
            request == null ? null : (String) request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE),
            request == null ? null : request.getRemoteAddr(),
            request == null ? null : request.getHeader("User-Agent"),
            details.before(),
            details.after()
        );
    }

    private AuditLog createPlatformAdmin(
        UUID companyId,
        UUID platformAdminId,
        AuditAction action,
        String entityType,
        UUID entityId,
        String result,
        AuditDetails details
    ) {
        HttpServletRequest request = currentRequest();
        return AuditLog.createPlatformAdmin(
            companyId,
            platformAdminId,
            action.name(),
            entityType,
            entityId,
            result,
            request == null ? null : (String) request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE),
            request == null ? null : request.getRemoteAddr(),
            request == null ? null : request.getHeader("User-Agent"),
            details.before(),
            details.after()
        );
    }

    private AuditDetails details(AuditAction action) {
        return switch (action) {
            case COMPANY_CREATE, USER_CREATE, CURRENT_ACCOUNT_CREATE, CASH_ACCOUNT_CREATE,
                BANK_ACCOUNT_CREATE, VOUCHER_CREATE, VOUCHER_TRANSFER_CREATE, PRODUCT_CREATE, INVOICE_CREATE -> AuditDetails.created();
            case USER_DISABLE, PRODUCT_DISABLE,
                CASH_ACCOUNT_DISABLE, BANK_ACCOUNT_DISABLE ->
                AuditDetails.transition("status", "ACTIVE", "PASSIVE");
            case VOUCHER_APPROVE -> AuditDetails.transition("status", "DRAFT", "APPROVED");
            case VOUCHER_CANCEL -> AuditDetails.transition("status", "APPROVED", "CANCELLED");
            case INVOICE_APPROVE -> AuditDetails.transition("status", "DRAFT", "APPROVED");
            default -> AuditDetails.empty();
        };
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
