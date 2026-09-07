package com.yigitcicekci.tillora.shared.security;

import java.util.Set;
import java.util.UUID;

public record AuthenticatedPrincipal(
    UUID userId,
    UUID companyId,
    String username,
    Set<String> authorities
) {
}
