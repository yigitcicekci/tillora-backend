package com.yigitcicekci.tillora.platformadmin.infrastructure.security;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.shared.security.LoginAttemptRateLimiter;
import org.springframework.stereotype.Component;

@Component
public class PlatformAdminLoginRateLimiter {

    private final LoginAttemptRateLimiter rateLimiter;
    private final AuditLogService auditLogService;

    public PlatformAdminLoginRateLimiter(
        LoginAttemptRateLimiter rateLimiter,
        AuditLogService auditLogService
    ) {
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
    }

    public void check(String email, String remoteAddress) {
        try {
            rateLimiter.check("platform-admin-login", email, remoteAddress);
        } catch (BusinessException exception) {
            if ("LOGIN_RATE_LIMIT_EXCEEDED".equals(exception.code())) {
                auditLogService.recordPlatformAdminRequiresNew(
                    null,
                    null,
                    AuditAction.LOGIN_FAILURE,
                    "PLATFORM_ADMIN",
                    null,
                    "FAILURE"
                );
            }
            throw exception;
        }
    }
}
