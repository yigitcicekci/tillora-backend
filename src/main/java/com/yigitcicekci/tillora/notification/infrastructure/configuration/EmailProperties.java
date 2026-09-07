package com.yigitcicekci.tillora.notification.infrastructure.configuration;

import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties("tillora.email")
public record EmailProperties(
    boolean enabled,
    EmailProviderType provider,
    String apiKey,
    String fromAddress,
    String fromName,
    String replyTo,
    Duration connectTimeout,
    Duration readTimeout,
    int maxRecipients,
    DataSize maxAttachmentSize
) {
    @Override
    public String toString() {
        return "EmailProperties[enabled=" + enabled
            + ", provider=" + provider
            + ", apiKey=[REDACTED]"
            + ", fromAddress=" + fromAddress
            + ", fromName=" + fromName
            + ", replyTo=" + replyTo
            + ", connectTimeout=" + connectTimeout
            + ", readTimeout=" + readTimeout
            + ", maxRecipients=" + maxRecipients
            + ", maxAttachmentSize=" + maxAttachmentSize + "]";
    }
}
