package com.yigitcicekci.tillora.auth.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.auth.api.request.ChangePasswordRequest;
import com.yigitcicekci.tillora.auth.api.response.AuthResponse;
import com.yigitcicekci.tillora.auth.api.response.AuthUserResponse;
import com.yigitcicekci.tillora.auth.application.service.AuthenticationResult;
import com.yigitcicekci.tillora.auth.application.service.AuthenticationService;
import com.yigitcicekci.tillora.auth.infrastructure.security.LoginRateLimiter;
import com.yigitcicekci.tillora.auth.infrastructure.security.RefreshTokenCookieService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

class AuthControllerTest {

    private final AuthenticationService authenticationService = mock(AuthenticationService.class);
    private final AuthController controller = new AuthController(
        authenticationService,
        new RefreshTokenCookieService(false, "Strict"),
        mock(LoginRateLimiter.class)
    );

    @Test
    void changesAuthenticatedUsersPasswordAndSetsReplacementRefreshCookie() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(userId, companyId, "user", Set.of());
        ChangePasswordRequest request = new ChangePasswordRequest("current-password", "new-password-123");
        Instant accessExpiresAt = Instant.now().plusSeconds(900);
        Instant refreshExpiresAt = Instant.now().plusSeconds(3600);
        AuthResponse authResponse = new AuthResponse(
            "new-access",
            accessExpiresAt,
            new AuthUserResponse(
                userId,
                companyId,
                "user",
                "user@example.com",
                "Test",
                "User",
                false,
                Set.of("ADMIN"),
                Set.of()
            )
        );
        when(authenticationService.changePassword(companyId, userId, request)).thenReturn(
            new AuthenticationResult(authResponse, "new-refresh", refreshExpiresAt)
        );

        ResponseEntity<AuthResponse> response = controller.changePassword(principal, request);

        assertThat(response.getBody()).isEqualTo(authResponse);
        assertThat(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE))
            .contains("TILLORA_REFRESH_TOKEN=new-refresh")
            .contains("HttpOnly");
        verify(authenticationService).changePassword(companyId, userId, request);
    }
}
