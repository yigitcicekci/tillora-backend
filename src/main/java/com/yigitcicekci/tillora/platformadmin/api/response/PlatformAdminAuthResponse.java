package com.yigitcicekci.tillora.platformadmin.api.response;

import java.time.Instant;

public record PlatformAdminAuthResponse(
    String accessToken,
    Instant accessTokenExpiresAt,
    PlatformAdminResponse platformAdmin
) {
}
