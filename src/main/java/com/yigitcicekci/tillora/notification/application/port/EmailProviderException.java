package com.yigitcicekci.tillora.notification.application.port;

public class EmailProviderException extends RuntimeException {

    private final String code;

    public EmailProviderException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
