package com.yigitcicekci.tillora.auth.infrastructure.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yigitcicekci.tillora.shared.error.ApiErrorResponse;
import com.yigitcicekci.tillora.shared.error.CorrelationIdFilter;
import com.yigitcicekci.tillora.shared.error.ErrorMessageResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

@Component
public class SecurityErrorWriter {

    private final ObjectMapper objectMapper;
    private final ErrorMessageResolver errorMessageResolver;

    public SecurityErrorWriter(ErrorMessageResolver errorMessageResolver) {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.errorMessageResolver = errorMessageResolver;
    }

    public void unauthorized(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED");
    }

    public void forbidden(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(request, response, HttpServletResponse.SC_FORBIDDEN, "FORBIDDEN");
    }

    public void passwordChangeRequired(HttpServletRequest request, HttpServletResponse response) throws IOException {
        write(
            request,
            response,
            HttpServletResponse.SC_FORBIDDEN,
            "PASSWORD_CHANGE_REQUIRED"
        );
    }

    private void write(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
        ErrorMessageResolver.LocalizedError localized = errorMessageResolver.resolve(code, request);
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Content-Language", localized.locale().toLanguageTag());
        objectMapper.writeValue(response.getWriter(), new ApiErrorResponse(
            code,
            localized.message(),
            request.getRequestURI(),
            OffsetDateTime.now(),
            (String) request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
        ));
    }
}
