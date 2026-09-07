package com.yigitcicekci.tillora.platformadmin.api.controller;

import com.yigitcicekci.tillora.platformadmin.api.request.PlatformAdminChangePasswordRequest;
import com.yigitcicekci.tillora.platformadmin.api.request.PlatformAdminLoginRequest;
import com.yigitcicekci.tillora.platformadmin.api.response.PlatformAdminAuthResponse;
import com.yigitcicekci.tillora.platformadmin.api.response.PlatformAdminResponse;
import com.yigitcicekci.tillora.platformadmin.application.service.PlatformAdminAuthenticationResult;
import com.yigitcicekci.tillora.platformadmin.application.service.PlatformAdminAuthenticationService;
import com.yigitcicekci.tillora.platformadmin.infrastructure.security.PlatformAdminLoginRateLimiter;
import com.yigitcicekci.tillora.platformadmin.infrastructure.security.PlatformAdminPrincipal;
import com.yigitcicekci.tillora.platformadmin.infrastructure.security.PlatformAdminRefreshTokenCookieService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/auth")
public class PlatformAdminAuthController {

    private final PlatformAdminAuthenticationService authenticationService;
    private final PlatformAdminRefreshTokenCookieService refreshTokenCookieService;
    private final PlatformAdminLoginRateLimiter loginRateLimiter;

    public PlatformAdminAuthController(
        PlatformAdminAuthenticationService authenticationService,
        PlatformAdminRefreshTokenCookieService refreshTokenCookieService,
        PlatformAdminLoginRateLimiter loginRateLimiter
    ) {
        this.authenticationService = authenticationService;
        this.refreshTokenCookieService = refreshTokenCookieService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/login")
    ResponseEntity<PlatformAdminAuthResponse> login(
        @Valid @RequestBody PlatformAdminLoginRequest request,
        HttpServletRequest servletRequest
    ) {
        loginRateLimiter.check(request.email(), servletRequest.getRemoteAddr());
        return authentication(authenticationService.login(request));
    }

    @PostMapping("/refresh")
    ResponseEntity<PlatformAdminAuthResponse> refresh(
        @CookieValue(name = PlatformAdminRefreshTokenCookieService.COOKIE_NAME, required = false) String refreshToken
    ) {
        return authentication(authenticationService.refresh(requiredRefreshToken(refreshToken)));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(
        @AuthenticationPrincipal PlatformAdminPrincipal principal,
        @CookieValue(name = PlatformAdminRefreshTokenCookieService.COOKIE_NAME, required = false) String refreshToken
    ) {
        if (refreshToken != null) {
            authenticationService.logout(principal.platformAdminId(), refreshToken);
        }
        return clearRefreshCookie();
    }

    @PostMapping("/change-password")
    ResponseEntity<Void> changePassword(
        @AuthenticationPrincipal PlatformAdminPrincipal principal,
        @Valid @RequestBody PlatformAdminChangePasswordRequest request
    ) {
        authenticationService.changePassword(principal.platformAdminId(), request);
        return clearRefreshCookie();
    }

    @PostMapping("/revoke-sessions")
    ResponseEntity<Void> revokeSessions(@AuthenticationPrincipal PlatformAdminPrincipal principal) {
        authenticationService.revokeSessions(principal.platformAdminId());
        return clearRefreshCookie();
    }

    @GetMapping("/me")
    PlatformAdminResponse me(@AuthenticationPrincipal PlatformAdminPrincipal principal) {
        return authenticationService.me(principal.platformAdminId());
    }

    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    private ResponseEntity<PlatformAdminAuthResponse> authentication(PlatformAdminAuthenticationResult result) {
        return ResponseEntity.ok()
            .header(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookieService.create(result.refreshToken(), result.refreshTokenExpiresAt()).toString()
            )
            .body(result.response());
    }

    private ResponseEntity<Void> clearRefreshCookie() {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clear().toString())
            .build();
    }

    private String requiredRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Invalid refresh token.", HttpStatus.UNAUTHORIZED);
        }
        return refreshToken;
    }
}
