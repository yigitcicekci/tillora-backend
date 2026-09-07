package com.yigitcicekci.tillora.platformadmin.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_admins")
public class PlatformAdmin {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled;

    private Instant lastLoginAt;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected PlatformAdmin() {
    }

    private PlatformAdmin(String email, String passwordHash) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static PlatformAdmin create(String email, String passwordHash) {
        return new PlatformAdmin(email, passwordHash);
    }

    public void markLoggedIn() {
        this.lastLoginAt = Instant.now();
        this.updatedAt = this.lastLoginAt;
    }

    public void changePassword(String passwordHash) {
        this.passwordHash = passwordHash;
        this.updatedAt = Instant.now();
    }

    public void disable() {
        this.enabled = false;
        this.updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public boolean enabled() {
        return enabled;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
