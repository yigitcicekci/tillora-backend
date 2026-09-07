package com.yigitcicekci.tillora.user.application.service;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.api.request.CreateUserRequest;
import com.yigitcicekci.tillora.user.api.response.UserResponse;
import com.yigitcicekci.tillora.user.domain.entity.Role;
import com.yigitcicekci.tillora.user.domain.entity.User;
import com.yigitcicekci.tillora.user.domain.enumeration.RoleName;
import com.yigitcicekci.tillora.user.domain.enumeration.UserStatus;
import com.yigitcicekci.tillora.user.domain.repository.RoleRepository;
import com.yigitcicekci.tillora.user.domain.repository.UserRepository;
import com.yigitcicekci.tillora.user.infrastructure.persistence.UserAdministrationLockRepository;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserAdministrationLockRepository userAdministrationLockRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public UserService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        UserAdministrationLockRepository userAdministrationLockRepository,
        PasswordEncoder passwordEncoder,
        AuditLogService auditLogService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.userAdministrationLockRepository = userAdministrationLockRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public UserResponse create(UUID companyId, UUID actorUserId, CreateUserRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(username)) {
            throw new BusinessException("USERNAME_ALREADY_EXISTS", "Username already exists.", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByCompanyIdAndEmailIgnoreCase(companyId, email)) {
            throw new BusinessException("USER_EMAIL_ALREADY_EXISTS", "User email already exists.", HttpStatus.CONFLICT);
        }
        Set<Role> roles = resolveRoles(companyId, request);
        User user = User.create(
            companyId,
            username,
            email,
            passwordEncoder.encode(request.temporaryPassword()),
            request.firstName(),
            request.lastName(),
            request.phone(),
            roles
        );
        User savedUser = userRepository.save(user);
        auditLogService.record(companyId, actorUserId, AuditAction.USER_CREATE, "USER", savedUser.id());
        return UserResponse.from(savedUser);
    }

    @Transactional
    public UUID createAppOwner(
        UUID companyId,
        String username,
        String email,
        String password,
        String firstName,
        String lastName
    ) {
        return createInitialAdmin(companyId, username, email, password, firstName, lastName, true);
    }

    @Transactional
    public UUID createCompanyAdmin(
        UUID companyId,
        String username,
        String email,
        String password,
        String firstName,
        String lastName
    ) {
        return createInitialAdmin(companyId, username, email, password, firstName, lastName, false);
    }

    private UUID createInitialAdmin(
        UUID companyId,
        String username,
        String email,
        String password,
        String firstName,
        String lastName,
        boolean appOwner
    ) {
        String normalizedUsername = username.trim();
        String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);
        if (userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new BusinessException("USERNAME_ALREADY_EXISTS", "Username already exists.", HttpStatus.CONFLICT);
        }
        if (userRepository.existsByCompanyIdAndEmailIgnoreCase(companyId, normalizedEmail)) {
            throw new BusinessException("USER_EMAIL_ALREADY_EXISTS", "User email already exists.", HttpStatus.CONFLICT);
        }
        Set<Role> roles = Set.copyOf(roleRepository.findByCompanyIdAndNameIn(companyId, Set.of(RoleName.ADMIN)));
        if (roles.size() != 1) {
            throw new BusinessException("ROLE_NOT_FOUND", "Role not found.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        User user = userRepository.save(appOwner
            ? User.createAppOwner(
                companyId,
                normalizedUsername,
                normalizedEmail,
                passwordEncoder.encode(password),
                firstName.trim(),
                lastName.trim(),
                roles
            )
            : User.create(
                companyId,
                normalizedUsername,
                normalizedEmail,
                passwordEncoder.encode(password),
                firstName.trim(),
                lastName.trim(),
                null,
                roles
            ));
        auditLogService.record(companyId, user.id(), AuditAction.USER_CREATE, "USER", user.id());
        return user.id();
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserInfo findForAuthentication(String email, String username) {
        return userRepository.findActiveLoginUser(email, username)
            .map(this::toAuthenticatedUserInfo)
            .orElseThrow(() -> new BusinessException("INVALID_CREDENTIALS", "Invalid credentials.", HttpStatus.UNAUTHORIZED));
    }

    @Transactional(readOnly = true)
    public AuthenticatedUserInfo getActiveAuthenticationUser(UUID userId) {
        return userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
            .map(this::toAuthenticatedUserInfo)
            .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Unauthorized.", HttpStatus.UNAUTHORIZED));
    }

    @Transactional
    public void markLoggedIn(UUID userId) {
        User user = userRepository.findByIdAndStatus(userId, UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Unauthorized.", HttpStatus.UNAUTHORIZED));
        user.markLoggedIn();
    }

    @Transactional
    public AuthenticatedUserInfo changePassword(
        UUID companyId,
        UUID userId,
        String currentPassword,
        String newPassword
    ) {
        User user = userRepository.findForPasswordChange(userId, companyId, UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException("UNAUTHORIZED", "Unauthorized.", HttpStatus.UNAUTHORIZED));
        if (!passwordEncoder.matches(currentPassword, user.passwordHash())) {
            throw new BusinessException("INVALID_CURRENT_PASSWORD", "Current password is invalid.");
        }
        if (passwordEncoder.matches(newPassword, user.passwordHash())) {
            throw new BusinessException("PASSWORD_REUSE_NOT_ALLOWED", "New password must be different from the current password.");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
        return toAuthenticatedUserInfo(user);
    }

    @Transactional(readOnly = true)
    public Page<UserResponse> list(UUID companyId, Pageable pageable) {
        Page<User> page = userRepository.findByCompanyId(companyId, pageable);
        List<UUID> ids = page.getContent().stream().map(User::id).toList();
        if (ids.isEmpty()) {
            return page.map(UserResponse::from);
        }
        Map<UUID, User> usersWithRoles = userRepository.findWithRolesByIdIn(ids).stream()
            .collect(Collectors.toMap(User::id, Function.identity()));
        return page.map(user -> UserResponse.from(usersWithRoles.get(user.id())));
    }

    @Transactional(readOnly = true)
    public UserResponse get(UUID companyId, UUID id) {
        return userRepository.findByIdAndCompanyId(id, companyId)
            .map(UserResponse::from)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public UserResponse disable(UUID companyId, UUID actorUserId, UUID id) {
        userAdministrationLockRepository.acquire(companyId);
        User user = userRepository.findForDisable(id, companyId, UserStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException("USER_NOT_FOUND", "User not found.", HttpStatus.NOT_FOUND));
        if (user.appOwner()) {
            throw new BusinessException("APP_OWNER_CANNOT_BE_DISABLED", "App owner cannot be disabled.", HttpStatus.CONFLICT);
        }
        if (user.roles().stream().anyMatch(role -> role.name() == RoleName.ADMIN)
            && userRepository.countByCompanyIdAndStatusAndRolesName(
                companyId,
                UserStatus.ACTIVE,
                RoleName.ADMIN
            ) <= 1) {
            throw new BusinessException(
                "LAST_ACTIVE_ADMIN_CANNOT_BE_DISABLED",
                "The last active administrator cannot be disabled.",
                HttpStatus.CONFLICT
            );
        }
        user.disable();
        auditLogService.record(companyId, actorUserId, AuditAction.USER_DISABLE, "USER", user.id());
        return UserResponse.from(user);
    }

    private Set<Role> resolveRoles(UUID companyId, CreateUserRequest request) {
        Set<Role> roles = Set.copyOf(roleRepository.findByCompanyIdAndNameIn(companyId, request.roles()));
        Set<RoleName> matchedNames = roles.stream()
            .map(Role::name)
            .collect(Collectors.toSet());
        if (!matchedNames.containsAll(request.roles())) {
            throw new BusinessException("ROLE_NOT_FOUND", "Role not found.", HttpStatus.BAD_REQUEST);
        }
        return roles;
    }

    private AuthenticatedUserInfo toAuthenticatedUserInfo(User user) {
        Set<String> roles = user.roles().stream()
            .map(role -> role.name().name())
            .collect(Collectors.toSet());
        Set<String> authorities = user.roles().stream()
            .flatMap(role -> role.permissions().stream())
            .map(permission -> permission.code())
            .collect(Collectors.toSet());
        if (user.appOwner()) {
            authorities.add("APP_OWNER");
        }
        return new AuthenticatedUserInfo(
            user.id(),
            user.companyId(),
            user.username(),
            user.email(),
            user.passwordHash(),
            user.firstName(),
            user.lastName(),
            user.status() == UserStatus.ACTIVE,
            user.mustChangePassword(),
            user.passwordChangedAt(),
            roles,
            authorities
        );
    }
}
