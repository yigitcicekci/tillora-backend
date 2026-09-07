package com.yigitcicekci.tillora.platformadmin.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yigitcicekci.tillora.platformadmin.domain.entity.PlatformAdmin;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformAdminJwtTokenServiceTest {

    private final PlatformAdminJwtTokenService tokenService = new PlatformAdminJwtTokenService(
        "platform-admin-test-secret-at-least-32-characters",
        Duration.ofMinutes(15),
        Duration.ofDays(7)
    );

    @Test
    void createsDistinctPlatformAdminAccessAndRefreshTokenTypes() {
        PlatformAdmin admin = PlatformAdmin.create("admin@example.com", "hash");
        PlatformAdminTokenPair pair = tokenService.createTokenPair(admin, UUID.randomUUID());

        VerifiedPlatformAdminToken access = tokenService.verifyAccessToken(pair.accessToken());
        VerifiedPlatformAdminToken refresh = tokenService.verifyRefreshToken(pair.refreshToken());

        assertThat(access.platformAdminId()).isEqualTo(admin.id());
        assertThat(access.tokenType()).isEqualTo("access");
        assertThat(access.authorities()).containsExactly("PLATFORM_ADMIN");
        assertThat(refresh.platformAdminId()).isEqualTo(admin.id());
        assertThat(refresh.tokenType()).isEqualTo("refresh");
        assertThat(refresh.tokenId()).isNotEqualTo(access.tokenId());
    }

    @Test
    void rejectsBlankAndShortSigningSecrets() {
        assertThatThrownBy(() -> new PlatformAdminJwtTokenService(
            " ".repeat(32),
            Duration.ofMinutes(15),
            Duration.ofDays(7)
        )).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new PlatformAdminJwtTokenService(
            "a".repeat(31),
            Duration.ofMinutes(15),
            Duration.ofDays(7)
        )).isInstanceOf(IllegalStateException.class);
    }
}
