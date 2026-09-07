package com.yigitcicekci.tillora.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.auth.application.service.JwtTokenService;
import com.yigitcicekci.tillora.auth.application.service.VerifiedJwtToken;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.shared.error.ErrorMessageResolver;
import com.yigitcicekci.tillora.user.application.service.AuthenticatedUserInfo;
import com.yigitcicekci.tillora.user.application.service.UserService;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class JwtAuthenticationFilterTest {

    private final JwtTokenService jwtTokenService = mock(JwtTokenService.class);
    private final UserService userService = mock(UserService.class);
    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(
        jwtTokenService,
        userService,
        new SecurityErrorWriter(new ErrorMessageResolver())
    );

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void blocksBusinessRequestsUntilPasswordIsChanged() throws Exception {
        prepareUser(true);
        MockHttpServletRequest request = request("GET", "/api/v1/current-accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"code\":\"PASSWORD_CHANGE_REQUIRED\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void permitsOnlyExactPasswordChangeRequest() throws Exception {
        prepareUser(true);
        MockHttpServletRequest allowedRequest = request("POST", "/api/v1/auth/change-password");
        MockFilterChain allowedChain = new MockFilterChain();

        filter.doFilter(allowedRequest, new MockHttpServletResponse(), allowedChain);

        assertThat(allowedChain.getRequest()).isSameAs(allowedRequest);
        SecurityContextHolder.clearContext();

        MockHttpServletRequest wrongMethodRequest = request("GET", "/api/v1/auth/change-password");
        MockHttpServletResponse wrongMethodResponse = new MockHttpServletResponse();
        MockFilterChain wrongMethodChain = new MockFilterChain();

        filter.doFilter(wrongMethodRequest, wrongMethodResponse, wrongMethodChain);

        assertThat(wrongMethodResponse.getStatus()).isEqualTo(403);
        assertThat(wrongMethodChain.getRequest()).isNull();
    }

    @Test
    void permitsBusinessRequestsAfterPasswordChange() throws Exception {
        prepareUser(false);
        MockHttpServletRequest request = request("GET", "/api/v1/current-accounts");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
    }

    @Test
    void rejectsAccessTokenAfterCredentialVersionChanges() throws Exception {
        prepareUser(false);
        doThrow(new BusinessException("TOKEN_REVOKED", "Token revoked.", HttpStatus.UNAUTHORIZED))
            .when(jwtTokenService)
            .verifyUserState(any(), any());
        MockHttpServletRequest request = request("GET", "/api/v1/current-accounts");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"");
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void ignoresStaleBearerTokenOnRefreshEndpoint() throws Exception {
        MockHttpServletRequest request = request("POST", "/api/v1/auth/refresh");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isSameAs(request);
        verifyNoInteractions(jwtTokenService, userService);
    }

    private void prepareUser(boolean mustChangePassword) {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        VerifiedJwtToken token = new VerifiedJwtToken(
            UUID.randomUUID(),
            userId,
            companyId,
            "user",
            Set.of("CURRENT_ACCOUNT_READ"),
            0,
            "access",
            Instant.now().plusSeconds(60)
        );
        AuthenticatedUserInfo user = new AuthenticatedUserInfo(
            userId,
            companyId,
            "user",
            "user@example.com",
            "hash",
            "Test",
            "User",
            true,
            mustChangePassword,
            null,
            Set.of("ADMIN"),
            Set.of("CURRENT_ACCOUNT_READ")
        );
        when(jwtTokenService.verifyAccessToken("access-token")).thenReturn(token);
        when(userService.getActiveAuthenticationUser(userId)).thenReturn(user);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer access-token");
        return request;
    }
}
