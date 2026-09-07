package com.yigitcicekci.tillora.platformadmin.application.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record VerifiedPlatformAdminToken(
    UUID tokenId,
    UUID platformAdminId,
    String email,
    Set<String> authorities,
    String tokenType,
    Instant expiresAt
) {
}
