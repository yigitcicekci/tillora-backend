package com.yigitcicekci.tillora.user.domain.entity;

import com.yigitcicekci.tillora.user.domain.enumeration.RoleName;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "roles")
public class Role {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private RoleName name;

    @Column(nullable = false)
    private Instant createdAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "role_permissions",
        joinColumns = @JoinColumn(name = "role_id"),
        inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<Permission> permissions = new LinkedHashSet<>();

    protected Role() {
    }

    private Role(UUID companyId, RoleName name, Set<Permission> permissions) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.name = name;
        this.createdAt = Instant.now();
        this.permissions = new LinkedHashSet<>(permissions);
    }

    public static Role create(UUID companyId, RoleName name, Set<Permission> permissions) {
        return new Role(companyId, name, permissions);
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public RoleName name() {
        return name;
    }

    public Set<Permission> permissions() {
        return Set.copyOf(permissions);
    }
}
