package com.yigitcicekci.tillora.auth.infrastructure.security;

import com.yigitcicekci.tillora.auth.application.service.JwtTokenService;
import com.yigitcicekci.tillora.auth.application.service.VerifiedJwtToken;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.user.application.service.AuthenticatedUserInfo;
import com.yigitcicekci.tillora.user.application.service.UserService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Set<String> JWT_IGNORED_REQUESTS = Set.of(
        "GET /api/v1/auth/csrf",
        "POST /api/v1/auth/login",
        "POST /api/v1/auth/refresh"
    );
    private static final Set<String> PASSWORD_CHANGE_ALLOWED_REQUESTS = Set.of(
        "GET /api/v1/auth/me",
        "POST /api/v1/auth/change-password",
        "POST /api/v1/auth/logout"
    );

    private final JwtTokenService jwtTokenService;
    private final UserService userService;
    private final SecurityErrorWriter securityErrorWriter;

    public JwtAuthenticationFilter(
        JwtTokenService jwtTokenService,
        UserService userService,
        SecurityErrorWriter securityErrorWriter
    ) {
        this.jwtTokenService = jwtTokenService;
        this.userService = userService;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return (path.equals("/internal/admin") || path.startsWith("/internal/admin/"))
            || JWT_IGNORED_REQUESTS.contains(requestKey(request));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        try {
            VerifiedJwtToken verifiedToken = jwtTokenService.verifyAccessToken(authorization.substring(7));
            AuthenticatedUserInfo user = userService.getActiveAuthenticationUser(verifiedToken.userId());
            jwtTokenService.verifyUserState(verifiedToken, user);
            if (passwordChangeRequired(request, user)) {
                SecurityContextHolder.clearContext();
                securityErrorWriter.passwordChangeRequired(request, response);
                return;
            }
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(user.id(), user.companyId(), user.username(), user.authorities());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                user.authorities().stream().map(SimpleGrantedAuthority::new).toList()
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            securityErrorWriter.unauthorized(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private boolean passwordChangeRequired(HttpServletRequest request, AuthenticatedUserInfo user) {
        String path = requestPath(request);
        return user.mustChangePassword()
            && path.startsWith("/api/")
            && !"OPTIONS".equals(request.getMethod())
            && !PASSWORD_CHANGE_ALLOWED_REQUESTS.contains(requestKey(request));
    }

    private String requestKey(HttpServletRequest request) {
        return request.getMethod() + " " + requestPath(request);
    }

    private String requestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return request.getRequestURI().substring(contextPath.length());
    }
}
