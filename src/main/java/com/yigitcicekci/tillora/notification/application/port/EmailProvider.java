package com.yigitcicekci.tillora.notification.application.port;

public interface EmailProvider {

    boolean enabled();

    EmailSendResult send(EmailMessage message);
}
