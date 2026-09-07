package com.yigitcicekci.tillora.auth.infrastructure.security;

import com.yigitcicekci.tillora.platformadmin.application.service.PlatformAdminAuthenticationService;
import com.yigitcicekci.tillora.platformadmin.application.service.PlatformAdminJwtTokenService;
import com.yigitcicekci.tillora.platformadmin.application.service.VerifiedPlatformAdminToken;
import com.yigitcicekci.tillora.platformadmin.infrastructure.security.PlatformAdminPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class PlatformAdminJwtAuthenticationFilter extends OncePerRequestFilter {

    private final PlatformAdminJwtTokenService tokenService;
    private final PlatformAdminAuthenticationService authenticationService;
    private final SecurityErrorWriter securityErrorWriter;

    public PlatformAdminJwtAuthenticationFilter(
        PlatformAdminJwtTokenService tokenService,
        PlatformAdminAuthenticationService authenticationService,
        SecurityErrorWriter securityErrorWriter
    ) {
        this.tokenService = tokenService;
        this.authenticationService = authenticationService;
        this.securityErrorWriter = securityErrorWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = requestPath(request);
        return !path.equals("/internal/admin") && !path.startsWith("/internal/admin/");
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
            VerifiedPlatformAdminToken verifiedToken = tokenService.verifyAccessToken(authorization.substring(7));
            PlatformAdminPrincipal principal = authenticationService.getActivePrincipal(verifiedToken.platformAdminId());
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                principal,
                null,
                java.util.Set.of(new SimpleGrantedAuthority("PLATFORM_ADMIN"))
            );
            SecurityContextHolder.getContext().setAuthentication(authentication);
        } catch (RuntimeException exception) {
            SecurityContextHolder.clearContext();
            securityErrorWriter.unauthorized(request, response);
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String requestPath(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        return request.getRequestURI().substring(contextPath.length());
    }
}
