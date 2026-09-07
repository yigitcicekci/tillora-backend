package com.yigitcicekci.tillora.notification.infrastructure.provider;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.yigitcicekci.tillora.notification.application.port.EmailMessage;
import com.yigitcicekci.tillora.notification.application.port.EmailProvider;
import com.yigitcicekci.tillora.notification.application.port.EmailProviderException;
import com.yigitcicekci.tillora.notification.application.port.EmailSendResult;
import com.yigitcicekci.tillora.notification.infrastructure.configuration.EmailProperties;
import java.util.List;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

public class BrevoEmailProvider implements EmailProvider {

    private final WebClient webClient;
    private final EmailProperties properties;

    public BrevoEmailProvider(WebClient webClient, EmailProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public boolean enabled() {
        return true;
    }

    @Override
    public EmailSendResult send(EmailMessage message) {
        BrevoSendRequest request = new BrevoSendRequest(
            new BrevoAddress(properties.fromAddress(), properties.fromName()),
            addresses(message.to()),
            message.cc().isEmpty() ? null : addresses(message.cc()),
            blank(properties.replyTo())
                ? null
                : new BrevoAddress(properties.replyTo().trim(), null),
            message.subject(),
            message.htmlContent(),
            message.textContent(),
            message.attachments().stream()
                .map(attachment -> new BrevoAttachment(
                    attachment.fileName(),
                    attachment.content()
                ))
                .toList()
        );
        try {
            BrevoSendResponse response = webClient.post()
                .uri("/smtp/email")
                .header("api-key", properties.apiKey())
                .bodyValue(request)
                .exchangeToMono(clientResponse -> response(clientResponse.statusCode(), clientResponse))
                .block(properties.readTimeout().plusSeconds(1));
            if (response == null || blank(response.messageId())) {
                throw new EmailProviderException(
                    "EMAIL_PROVIDER_MALFORMED_RESPONSE",
                    "Email provider returned an invalid response."
                );
            }
            return new EmailSendResult(response.messageId().trim());
        } catch (EmailProviderException exception) {
            throw exception;
        } catch (WebClientRequestException exception) {
            throw new EmailProviderException(
                timedOut(exception)
                    ? "EMAIL_PROVIDER_TIMEOUT"
                    : "EMAIL_PROVIDER_UNAVAILABLE",
                timedOut(exception)
                    ? "Email provider request timed out."
                    : "Email provider is unavailable."
            );
        } catch (IllegalStateException exception) {
            throw new EmailProviderException(
                "EMAIL_PROVIDER_TIMEOUT",
                "Email provider request timed out."
            );
        } catch (WebClientException exception) {
            throw new EmailProviderException(
                "EMAIL_PROVIDER_MALFORMED_RESPONSE",
                "Email provider returned an invalid response."
            );
        } catch (RuntimeException exception) {
            throw new EmailProviderException(
                "EMAIL_PROVIDER_MALFORMED_RESPONSE",
                "Email provider returned an invalid response."
            );
        }
    }

    private Mono<BrevoSendResponse> response(
        HttpStatusCode status,
        org.springframework.web.reactive.function.client.ClientResponse response
    ) {
        if (status.is2xxSuccessful()) {
            return response.bodyToMono(BrevoSendResponse.class);
        }
        String code;
        String message;
        if (status.value() == 429) {
            code = "EMAIL_PROVIDER_RATE_LIMITED";
            message = "Email provider rate limit was exceeded.";
        } else if (status.is4xxClientError()) {
            code = "EMAIL_PROVIDER_REJECTED";
            message = "Email provider rejected the request.";
        } else {
            code = "EMAIL_PROVIDER_UNAVAILABLE";
            message = "Email provider is unavailable.";
        }
        return response.releaseBody().then(Mono.error(new EmailProviderException(code, message)));
    }

    private List<BrevoAddress> addresses(List<String> values) {
        return values.stream().map(value -> new BrevoAddress(value, null)).toList();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private boolean timedOut(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BrevoSendRequest(
        BrevoAddress sender,
        List<BrevoAddress> to,
        List<BrevoAddress> cc,
        BrevoAddress replyTo,
        String subject,
        String htmlContent,
        String textContent,
        List<BrevoAttachment> attachment
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record BrevoAddress(String email, String name) {
    }

    record BrevoAttachment(String name, byte[] content) {
    }

    record BrevoSendResponse(String messageId) {
    }
}
