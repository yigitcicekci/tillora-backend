package com.yigitcicekci.tillora.auth.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_refresh_tokens")
public class AuthRefreshToken {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID userId;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 64)
    private String tokenHash;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected AuthRefreshToken() {
    }

    private AuthRefreshToken(UUID id, UUID userId, UUID companyId, String tokenHash, Instant expiresAt) {
        this.id = id;
        this.userId = userId;
        this.companyId = companyId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = Instant.now();
    }

    public static AuthRefreshToken create(UUID id, UUID userId, UUID companyId, String tokenHash, Instant expiresAt) {
        return new AuthRefreshToken(id, userId, companyId, tokenHash, expiresAt);
    }

    public void revoke() {
        this.revokedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public UUID userId() {
        return userId;
    }

    public UUID companyId() {
        return companyId;
    }

    public String tokenHash() {
        return tokenHash;
    }

    public Instant expiresAt() {
        return expiresAt;
    }

    public boolean active() {
        return revokedAt == null && expiresAt.isAfter(Instant.now());
    }
}
