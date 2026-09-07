package com.yigitcicekci.tillora.notification.web.response;

import com.yigitcicekci.tillora.notification.domain.entity.EmailDelivery;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryStatus;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import java.time.Instant;
import java.util.UUID;

public record EmailDeliveryResponse(
    UUID id,
    EmailDeliveryStatus status,
    EmailProviderType provider,
    String providerMessageId,
    Instant createdAt,
    Instant sentAt,
    Instant failedAt
) {
    public static EmailDeliveryResponse from(EmailDelivery delivery) {
        return new EmailDeliveryResponse(
            delivery.id(),
            delivery.status(),
            delivery.provider(),
            delivery.providerMessageId(),
            delivery.createdAt(),
            delivery.sentAt(),
            delivery.failedAt()
        );
    }
}
