package com.yigitcicekci.tillora.auth.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.DefaultCorsProcessor;

class AuthSecurityConfigurationTest {

    @Test
    void allowsCredentialedFrontendRequests() {
        CorsConfigurationSource source = new AuthSecurityConfiguration()
            .corsConfigurationSource(List.of("https://app.example.com", "https://admin.example.com"));
        CorsConfiguration configuration = source.getCorsConfiguration(
            new MockHttpServletRequest("OPTIONS", "/api/v1/auth/refresh")
        );

        assertThat(configuration).isNotNull();
        assertThat(configuration.getAllowedOrigins())
            .containsExactly("https://app.example.com", "https://admin.example.com");
        assertThat(configuration.getAllowCredentials()).isTrue();
        assertThat(configuration.getAllowedHeaders()).contains("X-XSRF-TOKEN", "Idempotency-Key");
        assertThat(configuration.checkOrigin("https://app.example.com")).isEqualTo("https://app.example.com");
        assertThat(configuration.checkOrigin("https://admin.example.com")).isEqualTo("https://admin.example.com");
        assertThat(configuration.checkOrigin("https://unknown.example")).isNull();
    }

    @Test
    void processesFrontendPreflightAndRejectsUnknownOrigin() throws Exception {
        CorsConfiguration configuration = new AuthSecurityConfiguration()
            .corsConfigurationSource(List.of("https://app.example.com"))
            .getCorsConfiguration(new MockHttpServletRequest("OPTIONS", "/api/v1/auth/refresh"));
        DefaultCorsProcessor processor = new DefaultCorsProcessor();
        MockHttpServletRequest request = preflight("https://app.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(processor.processRequest(configuration, request, response)).isTrue();
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)).isEqualTo("https://app.example.com");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)).isEqualTo("true");
        assertThat(response.getHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS)).containsIgnoringCase("X-XSRF-TOKEN");

        MockHttpServletResponse rejectedResponse = new MockHttpServletResponse();
        assertThat(processor.processRequest(configuration, preflight("https://unknown.example"), rejectedResponse)).isFalse();
        assertThat(rejectedResponse.getStatus()).isEqualTo(403);
    }

    private MockHttpServletRequest preflight(String origin) {
        MockHttpServletRequest request = new MockHttpServletRequest("OPTIONS", "/api/v1/auth/refresh");
        request.addHeader(HttpHeaders.ORIGIN, origin);
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST");
        request.addHeader(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "X-XSRF-TOKEN");
        return request;
    }
}
