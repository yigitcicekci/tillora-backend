package com.yigitcicekci.tillora.notification.application.port;

public record EmailMessageAttachment(
    String fileName,
    String contentType,
    byte[] content
) {
}
