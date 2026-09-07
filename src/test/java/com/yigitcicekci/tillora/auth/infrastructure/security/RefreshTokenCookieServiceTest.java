package com.yigitcicekci.tillora.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefreshTokenCookieServiceTest {

    @Test
    void createsSecureHttpOnlyRefreshCookie() {
        RefreshTokenCookieService service = new RefreshTokenCookieService(true, "Lax");

        String cookie = service.create("refresh-token", Instant.now().plusSeconds(60)).toString();

        assertThat(cookie)
            .contains("TILLORA_REFRESH_TOKEN=refresh-token")
            .contains("Path=/api/v1/auth")
            .contains("Secure")
            .contains("HttpOnly")
            .contains("SameSite=Lax");
    }
}
