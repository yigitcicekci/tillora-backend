package com.yigitcicekci.tillora.user.api.response;

import com.yigitcicekci.tillora.user.domain.entity.Role;
import com.yigitcicekci.tillora.user.domain.entity.User;
import com.yigitcicekci.tillora.user.domain.enumeration.RoleName;
import com.yigitcicekci.tillora.user.domain.enumeration.UserStatus;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public record UserResponse(
    UUID id,
    UUID companyId,
    String username,
    String email,
    String firstName,
    String lastName,
    String phone,
    UserStatus status,
    boolean mustChangePassword,
    Instant lastLoginAt,
    Instant passwordChangedAt,
    Instant createdAt,
    Instant updatedAt,
    Set<RoleName> roles
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.id(),
            user.companyId(),
            user.username(),
            user.email(),
            user.firstName(),
            user.lastName(),
            user.phone(),
            user.status(),
            user.mustChangePassword(),
            user.lastLoginAt(),
            user.passwordChangedAt(),
            user.createdAt(),
            user.updatedAt(),
            user.roles().stream().map(Role::name).collect(Collectors.toSet())
        );
    }
}
