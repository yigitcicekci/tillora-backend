package com.yigitcicekci.tillora.auth.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.auth.api.request.ChangePasswordRequest;
import com.yigitcicekci.tillora.auth.domain.repository.AuthRefreshTokenRepository;
import com.yigitcicekci.tillora.user.application.service.AuthenticatedUserInfo;
import com.yigitcicekci.tillora.user.application.service.UserService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class AuthenticationServiceTest {

    private final UserService userService = mock(UserService.class);
    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final AuthRefreshTokenRepository refreshTokenRepository = mock(AuthRefreshTokenRepository.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final AuthenticationService authenticationService = new AuthenticationService(
        userService,
        mock(PasswordEncoder.class),
        jwtTokenService,
        refreshTokenRepository,
        auditLogService
    );

    @Test
    void revokesExistingRefreshTokensAndIssuesReplacementAuthentication() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        Instant changedAt = Instant.now();
        AuthenticatedUserInfo user = new AuthenticatedUserInfo(
            userId,
            companyId,
            "user",
            "user@example.com",
            "new-hash",
            "Test",
            "User",
            true,
            false,
            changedAt,
            Set.of("ADMIN"),
            Set.of("USER_READ")
        );
        ChangePasswordRequest request = new ChangePasswordRequest("current-password", "new-password-123");
        Instant accessExpiresAt = Instant.now().plusSeconds(900);
        Instant refreshExpiresAt = Instant.now().plusSeconds(3600);
        JwtTokenPair pair = new JwtTokenPair("new-access", "new-refresh", accessExpiresAt, refreshExpiresAt);
        when(userService.changePassword(companyId, userId, request.currentPassword(), request.newPassword())).thenReturn(user);
        when(jwtTokenService.createTokenPair(any(AuthenticatedUserInfo.class), any(UUID.class))).thenReturn(pair);
        when(jwtTokenService.tokenHash("new-refresh")).thenReturn("refresh-hash");

        AuthenticationResult result = authenticationService.changePassword(companyId, userId, request);

        assertThat(result.response().accessToken()).isEqualTo("new-access");
        assertThat(result.response().user().mustChangePassword()).isFalse();
        assertThat(result.refreshToken()).isEqualTo("new-refresh");
        verify(refreshTokenRepository).revokeAllByUserIdAndCompanyId(
            org.mockito.ArgumentMatchers.eq(userId),
            org.mockito.ArgumentMatchers.eq(companyId),
            any(Instant.class)
        );
        verify(auditLogService).record(companyId, userId, AuditAction.PASSWORD_CHANGE, "USER", userId);
        verify(refreshTokenRepository).save(any());
    }
}
