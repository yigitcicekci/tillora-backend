package com.yigitcicekci.tillora.platformadmin.application.service;

import com.yigitcicekci.tillora.platformadmin.api.response.PlatformAdminAuthResponse;
import java.time.Instant;

public record PlatformAdminAuthenticationResult(
    PlatformAdminAuthResponse response,
    String refreshToken,
    Instant refreshTokenExpiresAt
) {
}
