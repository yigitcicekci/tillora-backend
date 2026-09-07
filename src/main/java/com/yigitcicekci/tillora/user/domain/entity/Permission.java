package com.yigitcicekci.tillora.user.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "permissions")
public class Permission {

    @Id
    private UUID id;

    @Column(nullable = false, length = 80)
    private String code;

    @Column(nullable = false)
    private Instant createdAt;

    protected Permission() {
    }

    public UUID id() {
        return id;
    }

    public String code() {
        return code;
    }
}
