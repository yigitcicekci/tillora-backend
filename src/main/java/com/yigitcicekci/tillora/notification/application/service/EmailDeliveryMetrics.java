package com.yigitcicekci.tillora.notification.application.service;

import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryStatus;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class EmailDeliveryMetrics {

    private final MeterRegistry registry;

    public EmailDeliveryMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(
        EmailProviderType provider,
        EmailDeliveryStatus status,
        long durationNanos
    ) {
        registry.counter(
            "tillora.email.delivery.total",
            "provider", provider.name(),
            "status", status.name()
        ).increment();
        Timer.builder("tillora.email.delivery.duration")
            .tag("provider", provider.name())
            .register(registry)
            .record(durationNanos, TimeUnit.NANOSECONDS);
    }
}
