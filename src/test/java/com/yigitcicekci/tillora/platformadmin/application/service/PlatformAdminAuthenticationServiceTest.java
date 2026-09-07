package com.yigitcicekci.tillora.platformadmin.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.platformadmin.api.request.PlatformAdminChangePasswordRequest;
import com.yigitcicekci.tillora.platformadmin.api.request.PlatformAdminLoginRequest;
import com.yigitcicekci.tillora.platformadmin.domain.entity.PlatformAdmin;
import com.yigitcicekci.tillora.platformadmin.domain.repository.PlatformAdminRefreshTokenRepository;
import com.yigitcicekci.tillora.platformadmin.domain.repository.PlatformAdminRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PlatformAdminAuthenticationServiceTest {

    private final PlatformAdminRepository platformAdminRepository = mock(PlatformAdminRepository.class);
    private final PlatformAdminRefreshTokenRepository refreshTokenRepository = mock(PlatformAdminRefreshTokenRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final PlatformAdminJwtTokenService tokenService = mock(PlatformAdminJwtTokenService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final PlatformAdminAuthenticationService authenticationService = new PlatformAdminAuthenticationService(
        platformAdminRepository,
        refreshTokenRepository,
        passwordEncoder,
        tokenService,
        auditLogService
    );

    @Test
    void rejectsDisabledPlatformAdmin() {
        PlatformAdmin admin = PlatformAdmin.create("admin@example.com", "hash");
        admin.disable();
        when(platformAdminRepository.findByEmailIgnoreCase("admin@example.com")).thenReturn(Optional.of(admin));

        assertThatThrownBy(() -> authenticationService.login(
            new PlatformAdminLoginRequest("admin@example.com", "password-123")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_CREDENTIALS");
        verify(auditLogService).recordPlatformAdminRequiresNew(
            null,
            admin.id(),
            com.yigitcicekci.tillora.audit.application.service.AuditAction.LOGIN_FAILURE,
            "PLATFORM_ADMIN",
            admin.id(),
            "FAILURE"
        );
    }

    @Test
    void bootstrapsOnlyWhenNoPlatformAdminExists() {
        when(platformAdminRepository.count()).thenReturn(1L);

        authenticationService.bootstrap("admin@example.com", "password-123");

        verify(platformAdminRepository).acquireBootstrapLock();
        verify(platformAdminRepository, org.mockito.Mockito.never()).saveAndFlush(any());
    }

    @Test
    void changesPasswordAndRevokesAllSessions() {
        PlatformAdmin admin = PlatformAdmin.create("admin@example.com", "current-hash");
        when(platformAdminRepository.findByIdAndEnabledTrue(admin.id())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("current-password", "current-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password", "current-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password")).thenReturn("new-hash");

        authenticationService.changePassword(
            admin.id(),
            new PlatformAdminChangePasswordRequest("current-password", "new-password")
        );

        verify(passwordEncoder).encode("new-password");
        verify(refreshTokenRepository).revokeAllByPlatformAdminId(eq(admin.id()), any(Instant.class));
        verify(auditLogService).recordPlatformAdmin(
            null,
            admin.id(),
            AuditAction.PLATFORM_ADMIN_PASSWORD_CHANGED,
            "PLATFORM_ADMIN",
            admin.id(),
            "SUCCESS"
        );
    }

    @Test
    void rejectsPasswordChangeWithWrongCurrentPassword() {
        PlatformAdmin admin = PlatformAdmin.create("admin@example.com", "current-hash");
        when(platformAdminRepository.findByIdAndEnabledTrue(admin.id())).thenReturn(Optional.of(admin));
        when(passwordEncoder.matches("wrong-password", "current-hash")).thenReturn(false);

        assertThatThrownBy(() -> authenticationService.changePassword(
            admin.id(),
            new PlatformAdminChangePasswordRequest("wrong-password", "new-password")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_CURRENT_PASSWORD");

        verify(passwordEncoder, never()).encode(any());
        verify(refreshTokenRepository, never()).revokeAllByPlatformAdminId(any(), any(Instant.class));
        verify(auditLogService, never()).recordPlatformAdmin(
            any(), any(), any(), any(), any(), any()
        );
    }

    @Test
    void revokesAllSessionsAndAuditsThePlatformAdmin() {
        PlatformAdmin admin = PlatformAdmin.create("admin@example.com", "hash");
        when(platformAdminRepository.findByIdAndEnabledTrue(admin.id())).thenReturn(Optional.of(admin));

        authenticationService.revokeSessions(admin.id());

        verify(refreshTokenRepository).revokeAllByPlatformAdminId(eq(admin.id()), any(Instant.class));
        verify(auditLogService).recordPlatformAdmin(
            null,
            admin.id(),
            AuditAction.PLATFORM_ADMIN_SESSIONS_REVOKED,
            "PLATFORM_ADMIN",
            admin.id(),
            "SUCCESS"
        );
    }
}
