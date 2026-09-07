package com.yigitcicekci.tillora.platformadmin.application.service;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.platformadmin.api.request.PlatformAdminChangePasswordRequest;
import com.yigitcicekci.tillora.platformadmin.api.request.PlatformAdminLoginRequest;
import com.yigitcicekci.tillora.platformadmin.api.response.PlatformAdminAuthResponse;
import com.yigitcicekci.tillora.platformadmin.api.response.PlatformAdminResponse;
import com.yigitcicekci.tillora.platformadmin.domain.entity.PlatformAdmin;
import com.yigitcicekci.tillora.platformadmin.domain.entity.PlatformAdminRefreshToken;
import com.yigitcicekci.tillora.platformadmin.domain.repository.PlatformAdminRefreshTokenRepository;
import com.yigitcicekci.tillora.platformadmin.domain.repository.PlatformAdminRepository;
import com.yigitcicekci.tillora.platformadmin.infrastructure.security.PlatformAdminPrincipal;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformAdminAuthenticationService {

    private final PlatformAdminRepository platformAdminRepository;
    private final PlatformAdminRefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final PlatformAdminJwtTokenService tokenService;
    private final AuditLogService auditLogService;

    public PlatformAdminAuthenticationService(
        PlatformAdminRepository platformAdminRepository,
        PlatformAdminRefreshTokenRepository refreshTokenRepository,
        PasswordEncoder passwordEncoder,
        PlatformAdminJwtTokenService tokenService,
        AuditLogService auditLogService
    ) {
        this.platformAdminRepository = platformAdminRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public PlatformAdminAuthenticationResult login(PlatformAdminLoginRequest request) {
        String email = normalizeEmail(request.email());
        PlatformAdmin admin = platformAdminRepository.findByEmailIgnoreCase(email).orElse(null);
        if (admin == null) {
            auditLogService.recordPlatformAdminRequiresNew(
                null,
                null,
                AuditAction.LOGIN_FAILURE,
                "PLATFORM_ADMIN",
                null,
                "FAILURE"
            );
            throw invalidCredentials();
        }
        if (!admin.enabled() || !passwordEncoder.matches(request.password(), admin.passwordHash())) {
            auditLogService.recordPlatformAdminRequiresNew(
                null,
                admin.id(),
                AuditAction.LOGIN_FAILURE,
                "PLATFORM_ADMIN",
                admin.id(),
                "FAILURE"
            );
            throw invalidCredentials();
        }
        admin.markLoggedIn();
        PlatformAdminAuthenticationResult result = createAuthentication(admin);
        auditLogService.recordPlatformAdmin(
            null,
            admin.id(),
            AuditAction.LOGIN_SUCCESS,
            "PLATFORM_ADMIN",
            admin.id(),
            "SUCCESS"
        );
        return result;
    }

    @Transactional
    public PlatformAdminAuthenticationResult refresh(String refreshToken) {
        VerifiedPlatformAdminToken verified = tokenService.verifyRefreshToken(refreshToken);
        PlatformAdminRefreshToken storedToken = refreshTokenRepository
            .findByIdAndTokenHash(verified.tokenId(), tokenService.tokenHash(refreshToken))
            .orElseThrow(() -> invalidRefreshToken("INVALID_REFRESH_TOKEN"));
        if (!verified.platformAdminId().equals(storedToken.platformAdminId())) {
            throw invalidRefreshToken("INVALID_REFRESH_TOKEN");
        }
        if (!storedToken.active()) {
            throw invalidRefreshToken("REFRESH_TOKEN_EXPIRED");
        }
        PlatformAdmin admin = getActiveAdmin(storedToken.platformAdminId());
        storedToken.revoke();
        return createAuthentication(admin);
    }

    @Transactional
    public void logout(UUID expectedAdminId, String refreshToken) {
        VerifiedPlatformAdminToken verified = tokenService.verifyRefreshToken(refreshToken);
        PlatformAdminRefreshToken storedToken = refreshTokenRepository
            .findByIdAndTokenHash(verified.tokenId(), tokenService.tokenHash(refreshToken))
            .orElseThrow(() -> invalidRefreshToken("INVALID_REFRESH_TOKEN"));
        if (!expectedAdminId.equals(storedToken.platformAdminId())
            || !verified.platformAdminId().equals(storedToken.platformAdminId())) {
            throw invalidRefreshToken("INVALID_REFRESH_TOKEN");
        }
        storedToken.revoke();
        auditLogService.recordPlatformAdmin(
            null,
            expectedAdminId,
            AuditAction.LOGOUT,
            "PLATFORM_ADMIN",
            expectedAdminId,
            "SUCCESS"
        );
    }

    @Transactional
    public void changePassword(UUID platformAdminId, PlatformAdminChangePasswordRequest request) {
        PlatformAdmin admin = getActiveAdmin(platformAdminId);
        if (!passwordEncoder.matches(request.currentPassword(), admin.passwordHash())) {
            throw new BusinessException("INVALID_CURRENT_PASSWORD", "Current password is invalid.");
        }
        if (passwordEncoder.matches(request.newPassword(), admin.passwordHash())) {
            throw new BusinessException(
                "PASSWORD_REUSE_NOT_ALLOWED",
                "New password must be different from the current password."
            );
        }
        admin.changePassword(passwordEncoder.encode(request.newPassword()));
        refreshTokenRepository.revokeAllByPlatformAdminId(platformAdminId, Instant.now());
        auditLogService.recordPlatformAdmin(
            null,
            platformAdminId,
            AuditAction.PLATFORM_ADMIN_PASSWORD_CHANGED,
            "PLATFORM_ADMIN",
            platformAdminId,
            "SUCCESS"
        );
    }

    @Transactional
    public void revokeSessions(UUID platformAdminId) {
        getActiveAdmin(platformAdminId);
        refreshTokenRepository.revokeAllByPlatformAdminId(platformAdminId, Instant.now());
        auditLogService.recordPlatformAdmin(
            null,
            platformAdminId,
            AuditAction.PLATFORM_ADMIN_SESSIONS_REVOKED,
            "PLATFORM_ADMIN",
            platformAdminId,
            "SUCCESS"
        );
    }

    @Transactional(readOnly = true)
    public PlatformAdminResponse me(UUID platformAdminId) {
        return PlatformAdminResponse.from(getActiveAdmin(platformAdminId));
    }

    @Transactional(readOnly = true)
    public PlatformAdminPrincipal getActivePrincipal(UUID platformAdminId) {
        PlatformAdmin admin = getActiveAdmin(platformAdminId);
        return new PlatformAdminPrincipal(admin.id(), admin.email());
    }

    @Transactional(readOnly = true)
    public PlatformAdmin getActiveAdmin(UUID platformAdminId) {
        return platformAdminRepository.findByIdAndEnabledTrue(platformAdminId)
            .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Unauthorized.", HttpStatus.UNAUTHORIZED));
    }

    @Transactional
    public void bootstrap(String email, String password) {
        platformAdminRepository.acquireBootstrapLock();
        if (platformAdminRepository.count() > 0) {
            return;
        }
        String normalizedEmail = normalizeEmail(email);
        if (normalizedEmail.isBlank() || password == null || password.length() < 10 || password.length() > 120) {
            throw new BusinessException(
                "PLATFORM_ADMIN_BOOTSTRAP_INVALID",
                "Platform admin bootstrap configuration is invalid.",
                HttpStatus.INTERNAL_SERVER_ERROR
            );
        }
        PlatformAdmin admin = platformAdminRepository.saveAndFlush(
            PlatformAdmin.create(normalizedEmail, passwordEncoder.encode(password))
        );
        auditLogService.recordPlatformAdmin(
            null,
            admin.id(),
            AuditAction.PLATFORM_ADMIN_BOOTSTRAP,
            "PLATFORM_ADMIN",
            admin.id(),
            "SUCCESS"
        );
    }

    private PlatformAdminAuthenticationResult createAuthentication(PlatformAdmin admin) {
        UUID refreshTokenId = UUID.randomUUID();
        PlatformAdminTokenPair tokenPair = tokenService.createTokenPair(admin, refreshTokenId);
        refreshTokenRepository.save(PlatformAdminRefreshToken.create(
            refreshTokenId,
            admin.id(),
            tokenService.tokenHash(tokenPair.refreshToken()),
            tokenPair.refreshTokenExpiresAt()
        ));
        return new PlatformAdminAuthenticationResult(
            new PlatformAdminAuthResponse(
                tokenPair.accessToken(),
                tokenPair.accessTokenExpiresAt(),
                PlatformAdminResponse.from(admin)
            ),
            tokenPair.refreshToken(),
            tokenPair.refreshTokenExpiresAt()
        );
    }

    private String normalizeEmail(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "Invalid credentials.", HttpStatus.UNAUTHORIZED);
    }

    private BusinessException invalidRefreshToken(String code) {
        String message = "REFRESH_TOKEN_EXPIRED".equals(code) ? "Refresh token expired." : "Invalid refresh token.";
        return new BusinessException(code, message, HttpStatus.UNAUTHORIZED);
    }
}
