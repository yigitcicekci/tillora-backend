package com.yigitcicekci.tillora.notification.infrastructure.provider;

import com.yigitcicekci.tillora.notification.application.port.EmailMessage;
import com.yigitcicekci.tillora.notification.application.port.EmailProvider;
import com.yigitcicekci.tillora.notification.application.port.EmailProviderException;
import com.yigitcicekci.tillora.notification.application.port.EmailSendResult;

public class DisabledEmailProvider implements EmailProvider {

    @Override
    public boolean enabled() {
        return false;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        throw new EmailProviderException("EMAIL_DISABLED", "Transactional email is disabled.");
    }
}
