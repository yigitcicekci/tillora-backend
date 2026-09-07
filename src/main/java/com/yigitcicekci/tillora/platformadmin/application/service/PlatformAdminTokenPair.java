package com.yigitcicekci.tillora.platformadmin.application.service;

import java.time.Instant;

public record PlatformAdminTokenPair(
    String accessToken,
    String refreshToken,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt
) {
}
