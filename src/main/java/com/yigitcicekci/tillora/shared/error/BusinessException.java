package com.yigitcicekci.tillora.shared.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

public class BusinessException extends RuntimeException {

    private final String code;
    private final HttpStatusCode status;

    public BusinessException(String code, String message) {
        this(code, message, HttpStatus.UNPROCESSABLE_CONTENT);
    }

    public BusinessException(String code, String message, HttpStatusCode status) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public HttpStatusCode status() {
        return status;
    }
}
