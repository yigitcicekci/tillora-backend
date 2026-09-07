package com.yigitcicekci.tillora.auth.application.service;

import com.yigitcicekci.tillora.auth.api.request.LoginRequest;
import com.yigitcicekci.tillora.auth.api.request.ChangePasswordRequest;
import com.yigitcicekci.tillora.auth.api.response.AuthResponse;
import com.yigitcicekci.tillora.auth.api.response.AuthUserResponse;
import com.yigitcicekci.tillora.auth.api.response.LogoutResponse;
import com.yigitcicekci.tillora.auth.domain.entity.AuthRefreshToken;
import com.yigitcicekci.tillora.auth.domain.repository.AuthRefreshTokenRepository;
import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.AuthenticatedUserInfo;
import com.yigitcicekci.tillora.user.application.service.UserService;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthenticationService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService jwtTokenService;
    private final AuthRefreshTokenRepository refreshTokenRepository;
    private final AuditLogService auditLogService;

    public AuthenticationService(
        UserService userService,
        PasswordEncoder passwordEncoder,
        JwtTokenService jwtTokenService,
        AuthRefreshTokenRepository refreshTokenRepository,
        AuditLogService auditLogService
    ) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenService = jwtTokenService;
        this.refreshTokenRepository = refreshTokenRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AuthenticationResult login(LoginRequest request) {
        AuthenticatedUserInfo user;
        try {
            user = userService.findForAuthentication(normalizeEmail(request.email()), normalize(request.username()));
        } catch (BusinessException exception) {
            auditLogService.recordRequiresNew(null, null, AuditAction.LOGIN_FAILURE, "USER", null);
            throw exception;
        }
        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            auditLogService.recordRequiresNew(user.companyId(), user.id(), AuditAction.LOGIN_FAILURE, "USER", user.id());
            throw new BusinessException("INVALID_CREDENTIALS", "Invalid credentials.", HttpStatus.UNAUTHORIZED);
        }
        userService.markLoggedIn(user.id());
        auditLogService.record(user.companyId(), user.id(), AuditAction.LOGIN_SUCCESS, "USER", user.id());
        return createAuthentication(user);
    }

    @Transactional
    public AuthenticationResult refresh(String refreshToken) {
        VerifiedJwtToken verified = jwtTokenService.verifyRefreshToken(refreshToken);
        AuthRefreshToken storedToken = refreshTokenRepository.findByIdAndTokenHash(verified.tokenId(), jwtTokenService.tokenHash(refreshToken))
            .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "Invalid refresh token.", HttpStatus.UNAUTHORIZED));
        if (!storedToken.active()) {
            throw new BusinessException("REFRESH_TOKEN_EXPIRED", "Refresh token expired.", HttpStatus.UNAUTHORIZED);
        }
        AuthenticatedUserInfo user = userService.getActiveAuthenticationUser(storedToken.userId());
        jwtTokenService.verifyUserState(verified, user);
        storedToken.revoke();
        return createAuthentication(user);
    }

    @Transactional
    public AuthenticationResult changePassword(UUID companyId, UUID userId, ChangePasswordRequest request) {
        AuthenticatedUserInfo user = userService.changePassword(
            companyId,
            userId,
            request.currentPassword(),
            request.newPassword()
        );
        refreshTokenRepository.revokeAllByUserIdAndCompanyId(userId, companyId, Instant.now());
        auditLogService.record(companyId, userId, AuditAction.PASSWORD_CHANGE, "USER", userId);
        return createAuthentication(user);
    }

    @Transactional
    public LogoutResponse logout(String refreshToken) {
        VerifiedJwtToken verified = jwtTokenService.verifyRefreshToken(refreshToken);
        refreshTokenRepository.findByIdAndTokenHash(verified.tokenId(), jwtTokenService.tokenHash(refreshToken))
            .ifPresent(token -> {
                token.revoke();
                auditLogService.record(token.companyId(), token.userId(), AuditAction.LOGOUT, "USER", token.userId());
            });
        return new LogoutResponse(true);
    }

    @Transactional(readOnly = true)
    public AuthUserResponse me(UUID userId) {
        return AuthUserResponse.from(userService.getActiveAuthenticationUser(userId));
    }

    private AuthenticationResult createAuthentication(AuthenticatedUserInfo user) {
        UUID refreshTokenId = UUID.randomUUID();
        JwtTokenPair tokenPair = jwtTokenService.createTokenPair(user, refreshTokenId);
        refreshTokenRepository.save(AuthRefreshToken.create(
            refreshTokenId,
            user.id(),
            user.companyId(),
            jwtTokenService.tokenHash(tokenPair.refreshToken()),
            tokenPair.refreshTokenExpiresAt()
        ));
        return new AuthenticationResult(
            new AuthResponse(tokenPair.accessToken(), tokenPair.accessTokenExpiresAt(), AuthUserResponse.from(user)),
            tokenPair.refreshToken(),
            tokenPair.refreshTokenExpiresAt()
        );
    }

    private String normalize(String value) {
        return value.trim();
    }

    private String normalizeEmail(String value) {
        return normalize(value).toLowerCase(Locale.ROOT);
    }
}
