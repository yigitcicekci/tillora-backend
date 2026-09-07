package com.yigitcicekci.tillora.platformadmin.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_admin_refresh_tokens")
public class PlatformAdminRefreshToken {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID platformAdminId;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected PlatformAdminRefreshToken() {
    }

    private PlatformAdminRefreshToken(
        UUID id,
        UUID platformAdminId,
        String tokenHash,
        Instant expiresAt
    ) {
        this.id = id;
        this.platformAdminId = platformAdminId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public static PlatformAdminRefreshToken create(
        UUID id,
        UUID platformAdminId,
        String tokenHash,
        Instant expiresAt
    ) {
        return new PlatformAdminRefreshToken(id, platformAdminId, tokenHash, expiresAt);
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public UUID platformAdminId() {
        return platformAdminId;
    }

    public boolean active() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
