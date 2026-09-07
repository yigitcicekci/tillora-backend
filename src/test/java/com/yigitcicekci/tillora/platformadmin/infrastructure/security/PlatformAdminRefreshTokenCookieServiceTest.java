package com.yigitcicekci.tillora.platformadmin.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class PlatformAdminRefreshTokenCookieServiceTest {

    @Test
    void createsHostOnlySecureHttpOnlyAdminRefreshCookie() {
        PlatformAdminRefreshTokenCookieService service = new PlatformAdminRefreshTokenCookieService(true, "Lax");

        String cookie = service.create("refresh-token", Instant.now().plusSeconds(60)).toString();

        assertThat(cookie)
            .contains("TILLORA_PLATFORM_ADMIN_REFRESH_TOKEN=refresh-token")
            .contains("Path=/internal/admin/auth")
            .contains("Secure")
            .contains("HttpOnly")
            .contains("SameSite=Lax")
            .doesNotContain("Domain=");
    }
}
