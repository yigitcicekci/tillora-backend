package com.yigitcicekci.tillora.user.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.domain.entity.Role;
import com.yigitcicekci.tillora.user.domain.entity.User;
import com.yigitcicekci.tillora.user.domain.enumeration.RoleName;
import com.yigitcicekci.tillora.user.domain.enumeration.UserStatus;
import com.yigitcicekci.tillora.user.domain.repository.RoleRepository;
import com.yigitcicekci.tillora.user.domain.repository.UserRepository;
import com.yigitcicekci.tillora.user.infrastructure.persistence.UserAdministrationLockRepository;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class UserServiceTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final UserAdministrationLockRepository userAdministrationLockRepository =
        mock(UserAdministrationLockRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    private final UserService userService = new UserService(
        userRepository,
        mock(RoleRepository.class),
        userAdministrationLockRepository,
        passwordEncoder,
        mock(AuditLogService.class)
    );

    @Test
    void changesPasswordAndClearsMandatoryFlag() {
        UUID companyId = UUID.randomUUID();
        User user = user(companyId);
        when(userRepository.findForPasswordChange(user.id(), companyId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("new-password-123", "old-hash")).thenReturn(false);
        when(passwordEncoder.encode("new-password-123")).thenReturn("new-hash");

        AuthenticatedUserInfo result = userService.changePassword(
            companyId,
            user.id(),
            "current-password",
            "new-password-123"
        );

        assertThat(result.passwordHash()).isEqualTo("new-hash");
        assertThat(result.mustChangePassword()).isFalse();
        assertThat(result.passwordChangedAt()).isNotNull();
        var firstChangedAt = result.passwordChangedAt();
        user.changePassword("newer-hash");
        assertThat(user.passwordChangedAt()).isAfter(firstChangedAt);
    }

    @Test
    void rejectsIncorrectCurrentPasswordWithoutMutation() {
        UUID companyId = UUID.randomUUID();
        User user = user(companyId);
        when(userRepository.findForPasswordChange(user.id(), companyId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("incorrect-password", "old-hash")).thenReturn(false);

        assertThatThrownBy(() -> userService.changePassword(
            companyId,
            user.id(),
            "incorrect-password",
            "new-password-123"
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_CURRENT_PASSWORD");
        assertThat(user.passwordHash()).isEqualTo("old-hash");
        assertThat(user.mustChangePassword()).isTrue();
        assertThat(user.passwordChangedAt()).isNull();
        verify(passwordEncoder, never()).encode("new-password-123");
    }

    @Test
    void rejectsReusingCurrentPassword() {
        UUID companyId = UUID.randomUUID();
        User user = user(companyId);
        when(userRepository.findForPasswordChange(user.id(), companyId, UserStatus.ACTIVE)).thenReturn(Optional.of(user));
        when(passwordEncoder.matches("current-password", "old-hash")).thenReturn(true);
        when(passwordEncoder.matches("same-password", "old-hash")).thenReturn(true);

        assertThatThrownBy(() -> userService.changePassword(
            companyId,
            user.id(),
            "current-password",
            "same-password"
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PASSWORD_REUSE_NOT_ALLOWED");
        verify(passwordEncoder, never()).encode("same-password");
    }

    @Test
    void rejectsDisablingLastActiveAdministrator() {
        UUID companyId = UUID.randomUUID();
        Role admin = Role.create(companyId, RoleName.ADMIN, Set.of());
        User user = User.create(
            companyId,
            "admin",
            "admin@example.com",
            "old-hash",
            "Admin",
            "User",
            null,
            Set.of(admin)
        );
        when(userRepository.findForDisable(user.id(), companyId, UserStatus.ACTIVE))
            .thenReturn(Optional.of(user));
        when(userRepository.countByCompanyIdAndStatusAndRolesName(
            companyId,
            UserStatus.ACTIVE,
            RoleName.ADMIN
        )).thenReturn(1L);

        assertThatThrownBy(() -> userService.disable(companyId, user.id(), user.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("LAST_ACTIVE_ADMIN_CANNOT_BE_DISABLED");
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        verify(userAdministrationLockRepository).acquire(companyId);
    }

    @Test
    void exposesAppOwnerAuthorityOutsideCompanyRoles() {
        UUID companyId = UUID.randomUUID();
        User owner = User.createAppOwner(
            companyId,
            "owner",
            "owner@example.com",
            "hash",
            "App",
            "Owner",
            Set.of()
        );
        when(userRepository.findByIdAndStatus(owner.id(), UserStatus.ACTIVE)).thenReturn(Optional.of(owner));

        AuthenticatedUserInfo result = userService.getActiveAuthenticationUser(owner.id());

        assertThat(result.authorities()).containsExactly("APP_OWNER");
    }

    @Test
    void rejectsDisablingAppOwner() {
        UUID companyId = UUID.randomUUID();
        User owner = User.createAppOwner(
            companyId,
            "owner",
            "owner@example.com",
            "hash",
            "App",
            "Owner",
            Set.of()
        );
        when(userRepository.findForDisable(owner.id(), companyId, UserStatus.ACTIVE)).thenReturn(Optional.of(owner));

        assertThatThrownBy(() -> userService.disable(companyId, owner.id(), owner.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("APP_OWNER_CANNOT_BE_DISABLED");
        assertThat(owner.status()).isEqualTo(UserStatus.ACTIVE);
    }

    private User user(UUID companyId) {
        return User.create(
            companyId,
            "user",
            "user@example.com",
            "old-hash",
            "Test",
            "User",
            null,
            Set.of()
        );
    }
}
