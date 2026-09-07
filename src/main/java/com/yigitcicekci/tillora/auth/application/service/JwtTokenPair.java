package com.yigitcicekci.tillora.auth.application.service;

import java.time.Instant;

public record JwtTokenPair(
    String accessToken,
    String refreshToken,
    Instant accessTokenExpiresAt,
    Instant refreshTokenExpiresAt
) {
}
