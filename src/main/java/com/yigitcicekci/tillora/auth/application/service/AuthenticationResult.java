package com.yigitcicekci.tillora.auth.application.service;

import com.yigitcicekci.tillora.auth.api.response.AuthResponse;
import java.time.Instant;

public record AuthenticationResult(
    AuthResponse response,
    String refreshToken,
    Instant refreshTokenExpiresAt
) {
}
