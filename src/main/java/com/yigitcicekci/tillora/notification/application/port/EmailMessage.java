package com.yigitcicekci.tillora.notification.application.port;

import java.util.List;

public record EmailMessage(
    List<String> to,
    List<String> cc,
    String subject,
    String textContent,
    String htmlContent,
    List<EmailMessageAttachment> attachments
) {
    public EmailMessage {
        to = List.copyOf(to);
        cc = List.copyOf(cc);
        attachments = List.copyOf(attachments);
    }
}
