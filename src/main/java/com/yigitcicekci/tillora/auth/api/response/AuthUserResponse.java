package com.yigitcicekci.tillora.auth.api.response;

import com.yigitcicekci.tillora.user.application.service.AuthenticatedUserInfo;
import java.util.Set;
import java.util.UUID;

public record AuthUserResponse(
    UUID id,
    UUID companyId,
    String username,
    String email,
    String firstName,
    String lastName,
    boolean mustChangePassword,
    Set<String> roles,
    Set<String> authorities
) {
    public static AuthUserResponse from(AuthenticatedUserInfo user) {
        return new AuthUserResponse(
            user.id(),
            user.companyId(),
            user.username(),
            user.email(),
            user.firstName(),
            user.lastName(),
            user.mustChangePassword(),
            user.roles(),
            user.authorities()
        );
    }
}
