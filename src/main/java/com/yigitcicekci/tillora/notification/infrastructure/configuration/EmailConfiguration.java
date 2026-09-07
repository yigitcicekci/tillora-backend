package com.yigitcicekci.tillora.notification.infrastructure.configuration;

import com.yigitcicekci.tillora.notification.application.port.EmailProvider;
import com.yigitcicekci.tillora.notification.application.service.EmailLimits;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import com.yigitcicekci.tillora.notification.infrastructure.provider.BrevoEmailProvider;
import com.yigitcicekci.tillora.notification.infrastructure.provider.DisabledEmailProvider;
import io.netty.channel.ChannelOption;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

@Configuration
@EnableConfigurationProperties(EmailProperties.class)
public class EmailConfiguration {

    @Bean
    EmailLimits emailLimits(EmailProperties properties) {
        if (properties.maxRecipients() < 1
            || properties.maxAttachmentSize() == null
            || properties.maxAttachmentSize().toBytes() < 1) {
            throw new IllegalStateException("Email limits are invalid.");
        }
        return new EmailLimits(
            properties.maxRecipients(),
            properties.maxAttachmentSize().toBytes()
        );
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "tillora.email",
        name = "enabled",
        havingValue = "true"
    )
    EmailProvider brevoEmailProvider(EmailProperties properties) {
        validateEnabled(properties);
        HttpClient httpClient = HttpClient.create()
            .option(
                ChannelOption.CONNECT_TIMEOUT_MILLIS,
                Math.toIntExact(properties.connectTimeout().toMillis())
            )
            .responseTimeout(properties.readTimeout());
        WebClient webClient = WebClient.builder()
            .baseUrl("https://api.brevo.com/v3")
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
        return new BrevoEmailProvider(webClient, properties);
    }

    @Bean
    @ConditionalOnMissingBean(EmailProvider.class)
    EmailProvider disabledEmailProvider() {
        return new DisabledEmailProvider();
    }

    private void validateEnabled(EmailProperties properties) {
        if (properties.provider() != EmailProviderType.BREVO
            || blank(properties.apiKey())
            || blank(properties.fromAddress())
            || blank(properties.fromName())
            || headerInvalid(properties.fromAddress())
            || headerInvalid(properties.fromName())
            || (!blank(properties.replyTo()) && headerInvalid(properties.replyTo()))
            || !properties.fromAddress().contains("@")
            || (!blank(properties.replyTo()) && !properties.replyTo().contains("@"))
            || invalid(properties.connectTimeout())
            || invalid(properties.readTimeout())) {
            throw new IllegalStateException(
                "Email is enabled but Brevo configuration is incomplete or invalid."
            );
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean invalid(java.time.Duration duration) {
        return duration == null || duration.isZero() || duration.isNegative();
    }

    private boolean headerInvalid(String value) {
        return value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0;
    }
}
