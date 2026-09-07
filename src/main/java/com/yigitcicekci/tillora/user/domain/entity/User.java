package com.yigitcicekci.tillora.user.domain.entity;

import com.yigitcicekci.tillora.user.domain.enumeration.UserStatus;
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
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 80)
    private String username;

    @Column(nullable = false, length = 160)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 40)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private UserStatus status;

    private Instant lastLoginAt;

    private Instant passwordChangedAt;

    @Column(nullable = false)
    private boolean mustChangePassword;

    @Column(nullable = false)
    private boolean appOwner;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = @JoinColumn(name = "user_id"),
        inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<Role> roles = new LinkedHashSet<>();

    protected User() {
    }

    private User(
        UUID companyId,
        String username,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        String phone,
        Set<Role> roles,
        boolean mustChangePassword,
        boolean appOwner
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.username = username;
        this.email = email;
        this.passwordHash = passwordHash;
        this.firstName = firstName;
        this.lastName = lastName;
        this.phone = phone;
        this.status = UserStatus.ACTIVE;
        this.mustChangePassword = mustChangePassword;
        this.appOwner = appOwner;
        this.createdAt = now;
        this.updatedAt = now;
        this.roles = new LinkedHashSet<>(roles);
    }

    public static User create(
        UUID companyId,
        String username,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        String phone,
        Set<Role> roles
    ) {
        return new User(companyId, username, email, passwordHash, firstName, lastName, phone, roles, true, false);
    }

    public static User createAppOwner(
        UUID companyId,
        String username,
        String email,
        String passwordHash,
        String firstName,
        String lastName,
        Set<Role> roles
    ) {
        return new User(companyId, username, email, passwordHash, firstName, lastName, null, roles, true, true);
    }

    public void disable() {
        this.status = UserStatus.PASSIVE;
        this.updatedAt = Instant.now();
    }

    public void markLoggedIn() {
        this.lastLoginAt = Instant.now();
        this.updatedAt = this.lastLoginAt;
    }

    public void changePassword(String passwordHash) {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        if (passwordChangedAt != null && !now.isAfter(passwordChangedAt)) {
            now = passwordChangedAt.plusMillis(1);
        }
        this.passwordHash = passwordHash;
        this.passwordChangedAt = now;
        this.mustChangePassword = false;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public String username() {
        return username;
    }

    public String email() {
        return email;
    }

    public String passwordHash() {
        return passwordHash;
    }

    public String firstName() {
        return firstName;
    }

    public String lastName() {
        return lastName;
    }

    public String phone() {
        return phone;
    }

    public UserStatus status() {
        return status;
    }

    public Instant lastLoginAt() {
        return lastLoginAt;
    }

    public Instant passwordChangedAt() {
        return passwordChangedAt;
    }

    public boolean mustChangePassword() {
        return mustChangePassword;
    }

    public boolean appOwner() {
        return appOwner;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Set<Role> roles() {
        return Set.copyOf(roles);
    }
}
