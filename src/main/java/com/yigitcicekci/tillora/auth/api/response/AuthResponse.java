package com.yigitcicekci.tillora.auth.api.response;

import java.time.Instant;

public record AuthResponse(
    String accessToken,
    Instant accessTokenExpiresAt,
    AuthUserResponse user
) {
}
