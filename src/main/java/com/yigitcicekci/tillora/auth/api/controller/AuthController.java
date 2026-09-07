package com.yigitcicekci.tillora.auth.api.controller;

import com.yigitcicekci.tillora.auth.api.request.LoginRequest;
import com.yigitcicekci.tillora.auth.api.request.ChangePasswordRequest;
import com.yigitcicekci.tillora.auth.api.response.AuthResponse;
import com.yigitcicekci.tillora.auth.api.response.AuthUserResponse;
import com.yigitcicekci.tillora.auth.api.response.LogoutResponse;
import com.yigitcicekci.tillora.auth.application.service.AuthenticationService;
import com.yigitcicekci.tillora.auth.application.service.AuthenticationResult;
import com.yigitcicekci.tillora.auth.infrastructure.security.LoginRateLimiter;
import com.yigitcicekci.tillora.auth.infrastructure.security.RefreshTokenCookieService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
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
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthenticationService authenticationService;
    private final RefreshTokenCookieService refreshTokenCookieService;
    private final LoginRateLimiter loginRateLimiter;

    public AuthController(
        AuthenticationService authenticationService,
        RefreshTokenCookieService refreshTokenCookieService,
        LoginRateLimiter loginRateLimiter
    ) {
        this.authenticationService = authenticationService;
        this.refreshTokenCookieService = refreshTokenCookieService;
        this.loginRateLimiter = loginRateLimiter;
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        loginRateLimiter.check(request.username(), servletRequest.getRemoteAddr());
        return authentication(authenticationService.login(request));
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(
        @CookieValue(name = RefreshTokenCookieService.COOKIE_NAME, required = false) String refreshToken
    ) {
        return authentication(authenticationService.refresh(requiredRefreshToken(refreshToken)));
    }

    @PostMapping("/logout")
    ResponseEntity<LogoutResponse> logout(
        @CookieValue(name = RefreshTokenCookieService.COOKIE_NAME, required = false) String refreshToken
    ) {
        LogoutResponse response = refreshToken == null ? new LogoutResponse(true) : authenticationService.logout(refreshToken);
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.clear().toString())
            .body(response);
    }

    @PostMapping("/change-password")
    ResponseEntity<AuthResponse> changePassword(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody ChangePasswordRequest request
    ) {
        return authentication(authenticationService.changePassword(
            principal.companyId(),
            principal.userId(),
            request
        ));
    }

    @GetMapping("/me")
    AuthUserResponse me(@AuthenticationPrincipal AuthenticatedPrincipal principal) {
        return authenticationService.me(principal.userId());
    }

    @GetMapping("/csrf")
    CsrfToken csrf(CsrfToken csrfToken) {
        return csrfToken;
    }

    private ResponseEntity<AuthResponse> authentication(AuthenticationResult result) {
        return ResponseEntity.ok()
            .header(HttpHeaders.SET_COOKIE, refreshTokenCookieService.create(result.refreshToken(), result.refreshTokenExpiresAt()).toString())
            .body(result.response());
    }

    private String requiredRefreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new BusinessException("INVALID_REFRESH_TOKEN", "Invalid refresh token.", HttpStatus.UNAUTHORIZED);
        }
        return refreshToken;
    }
}
