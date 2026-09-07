package com.yigitcicekci.tillora.user.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.user.api.request.CreateUserRequest;
import com.yigitcicekci.tillora.user.api.response.UserResponse;
import com.yigitcicekci.tillora.user.application.service.UserService;
import com.yigitcicekci.tillora.user.domain.enumeration.RoleName;
import com.yigitcicekci.tillora.user.domain.enumeration.UserStatus;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class UserControllerTest {

    private final UserService userService = mock(UserService.class);
    private final UserController controller = new UserController(userService);

    @Test
    void usesAuthenticatedCompanyForUserOperations() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(actorId, companyId, "admin", Set.of("USER_CREATE"));
        CreateUserRequest request = new CreateUserRequest(
            "new-user",
            "new-user@example.com",
            "temporary-password",
            "New",
            "User",
            null,
            Set.of(RoleName.VIEWER)
        );
        UserResponse response = new UserResponse(
            userId,
            companyId,
            "new-user",
            "new-user@example.com",
            "New",
            "User",
            null,
            UserStatus.ACTIVE,
            true,
            null,
            null,
            null,
            null,
            Set.of(RoleName.VIEWER)
        );
        when(userService.create(companyId, actorId, request)).thenReturn(response);

        controller.create(principal, request);
        controller.list(principal, PageRequest.of(0, 20));
        controller.get(principal, userId);
        controller.disable(principal, userId);

        verify(userService).create(companyId, actorId, request);
        verify(userService).list(companyId, PageRequest.of(0, 20));
        verify(userService).get(companyId, userId);
        verify(userService).disable(companyId, actorId, userId);
    }
}
