package com.yigitcicekci.tillora.shared.error;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";
    public static final String CORRELATION_ID_ATTRIBUTE = "correlationId";
    private static final Pattern SAFE_CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._-]{1,80}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String correlationId = resolveCorrelationId(request);
        request.setAttribute(CORRELATION_ID_ATTRIBUTE, correlationId);
        response.setHeader(CORRELATION_ID_HEADER, correlationId);
        MDC.put(CORRELATION_ID_ATTRIBUTE, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(CORRELATION_ID_ATTRIBUTE);
        }
    }

    private String resolveCorrelationId(HttpServletRequest request) {
        String value = request.getHeader(CORRELATION_ID_HEADER);
        return value != null && SAFE_CORRELATION_ID.matcher(value).matches()
            ? value
            : UUID.randomUUID().toString();
    }
}
