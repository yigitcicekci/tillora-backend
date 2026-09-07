package com.yigitcicekci.tillora.auth.application.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record VerifiedJwtToken(
    UUID tokenId,
    UUID userId,
    UUID companyId,
    String username,
    Set<String> authorities,
    long credentialVersion,
    String type,
    Instant expiresAt
) {
}
