package com.yigitcicekci.tillora.platformadmin.infrastructure.security;

import java.time.Duration;
import java.time.Instant;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class PlatformAdminRefreshTokenCookieService {

    public static final String COOKIE_NAME = "TILLORA_PLATFORM_ADMIN_REFRESH_TOKEN";

    private final boolean secure;
    private final String sameSite;

    public PlatformAdminRefreshTokenCookieService(
        @Value("${tillora.security.cookie.secure:true}") boolean secure,
        @Value("${tillora.security.cookie.same-site:Lax}") String sameSite
    ) {
        this.secure = secure;
        this.sameSite = sameSite;
    }

    public ResponseCookie create(String refreshToken, Instant expiresAt) {
        Duration maxAge = Duration.between(Instant.now(), expiresAt);
        return cookie(refreshToken, maxAge.isNegative() ? Duration.ZERO : maxAge);
    }

    public ResponseCookie clear() {
        return cookie("", Duration.ZERO);
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
            .httpOnly(true)
            .secure(secure)
            .sameSite(sameSite)
            .path("/internal/admin/auth")
            .maxAge(maxAge)
            .build();
    }
}
