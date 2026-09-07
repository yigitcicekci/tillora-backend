package com.yigitcicekci.tillora.platformadmin.api.response;

import com.yigitcicekci.tillora.platformadmin.domain.entity.PlatformAdmin;
import java.time.Instant;
import java.util.UUID;

public record PlatformAdminResponse(
    UUID id,
    String email,
    boolean enabled,
    Instant lastLoginAt,
    Instant createdAt,
    Instant updatedAt
) {
    public static PlatformAdminResponse from(PlatformAdmin admin) {
        return new PlatformAdminResponse(
            admin.id(),
            admin.email(),
            admin.enabled(),
            admin.lastLoginAt(),
            admin.createdAt(),
            admin.updatedAt()
        );
    }
}
