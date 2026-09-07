package com.yigitcicekci.tillora.user.application.service;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record AuthenticatedUserInfo(
    UUID id,
    UUID companyId,
    String username,
    String email,
    String passwordHash,
    String firstName,
    String lastName,
    boolean active,
    boolean mustChangePassword,
    Instant passwordChangedAt,
    Set<String> roles,
    Set<String> authorities
) {
}
