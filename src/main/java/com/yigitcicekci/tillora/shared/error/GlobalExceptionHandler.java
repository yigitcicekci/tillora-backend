package com.yigitcicekci.tillora.shared.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatusCode;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private final ErrorMessageResolver errorMessageResolver;

    public GlobalExceptionHandler(ErrorMessageResolver errorMessageResolver) {
        this.errorMessageResolver = errorMessageResolver;
    }

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiErrorResponse> handleBusinessException(BusinessException exception, HttpServletRequest request) {
        return response(exception.status(), exception.code(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiErrorResponse> handleValidationException(MethodArgumentNotValidException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ApiErrorResponse> handleConstraintViolationException(ConstraintViolationException exception, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", request);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ApiErrorResponse> handleDataIntegrityViolationException(HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DATA_CONFLICT", request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiErrorResponse> handleAccessDeniedException(HttpServletRequest request) {
        return response(HttpStatus.FORBIDDEN, "FORBIDDEN", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ApiErrorResponse> handleAuthenticationException(HttpServletRequest request) {
        return response(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiErrorResponse> handleException(Exception exception, HttpServletRequest request) {
        LOGGER.error("Unhandled request failure correlationId={}", request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE), exception);
        return response(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", request);
    }

    private ResponseEntity<ApiErrorResponse> response(HttpStatusCode status, String code, HttpServletRequest request) {
        ErrorMessageResolver.LocalizedError localized = errorMessageResolver.resolve(code, request);
        ApiErrorResponse body = new ApiErrorResponse(
            code,
            localized.message(),
            request.getRequestURI(),
            OffsetDateTime.now(),
            (String) request.getAttribute(CorrelationIdFilter.CORRELATION_ID_ATTRIBUTE)
        );
        return ResponseEntity.status(status)
            .header(HttpHeaders.CONTENT_LANGUAGE, localized.locale().toLanguageTag())
            .body(body);
    }
}
