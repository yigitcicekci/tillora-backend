package com.yigitcicekci.tillora.auth.infrastructure.security;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.shared.security.LoginAttemptRateLimiter;
import org.springframework.stereotype.Component;

@Component
public class LoginRateLimiter {

    private final LoginAttemptRateLimiter rateLimiter;
    private final AuditLogService auditLogService;

    public LoginRateLimiter(
        LoginAttemptRateLimiter rateLimiter,
        AuditLogService auditLogService
    ) {
        this.rateLimiter = rateLimiter;
        this.auditLogService = auditLogService;
    }

    public void check(String username, String remoteAddress) {
        try {
            rateLimiter.check("login", username, remoteAddress);
        } catch (BusinessException exception) {
            if ("LOGIN_RATE_LIMIT_EXCEEDED".equals(exception.code())) {
                auditLogService.recordRequiresNew(null, null, AuditAction.LOGIN_FAILURE, "USER", null);
            }
            throw exception;
        }
    }
}
