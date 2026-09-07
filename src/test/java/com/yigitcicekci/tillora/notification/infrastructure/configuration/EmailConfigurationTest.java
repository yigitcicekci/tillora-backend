package com.yigitcicekci.tillora.notification.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

class EmailConfigurationTest {

    private final EmailConfiguration configuration = new EmailConfiguration();

    @Test
    void failsClearlyWhenEnabledBrevoConfigurationIsIncomplete() {
        EmailProperties properties = new EmailProperties(
            true,
            EmailProviderType.BREVO,
            "",
            "billing@example.com",
            "Tillora",
            "",
            Duration.ofSeconds(3),
            Duration.ofSeconds(10),
            10,
            DataSize.ofMegabytes(25)
        );

        assertThatThrownBy(() -> configuration.brevoEmailProvider(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Email is enabled but Brevo configuration is incomplete or invalid.")
            .hasMessageNotContaining("api-key");
    }

    @Test
    void rejectsNonPositiveLimitsEvenWhenEmailIsDisabled() {
        EmailProperties properties = new EmailProperties(
            false,
            EmailProviderType.BREVO,
            "",
            "",
            "Tillora",
            "",
            Duration.ofSeconds(3),
            Duration.ofSeconds(10),
            0,
            DataSize.ofBytes(0)
        );

        assertThatThrownBy(() -> configuration.emailLimits(properties))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Email limits are invalid.");
    }
}
