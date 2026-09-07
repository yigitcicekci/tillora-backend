package com.yigitcicekci.tillora.notification.application.service;

import com.yigitcicekci.tillora.notification.domain.entity.EmailDelivery;

public record PreparedEmailDelivery(EmailDelivery delivery, boolean created) {
}
