package com.yigitcicekci.tillora.notification.infrastructure.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yigitcicekci.tillora.notification.application.port.EmailMessage;
import com.yigitcicekci.tillora.notification.application.port.EmailMessageAttachment;
import com.yigitcicekci.tillora.notification.application.port.EmailProviderException;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import com.yigitcicekci.tillora.notification.infrastructure.configuration.EmailProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import mockwebserver3.MockResponse;
import mockwebserver3.MockWebServer;
import mockwebserver3.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;

class BrevoEmailProviderTest {

    private static final String API_KEY = "test-only-super-secret-brevo-key";

    private MockWebServer server;
    private BrevoEmailProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        provider = new BrevoEmailProvider(
            WebClient.builder().baseUrl(server.url("/").toString()).build(),
            properties(Duration.ofMillis(100))
        );
    }

    @AfterEach
    void tearDown() throws Exception {
        server.close();
    }

    @Test
    void sendsExpectedBrevoDtoWithoutManualBase64Copy() throws Exception {
        server.enqueue(new MockResponse.Builder()
            .code(201)
            .addHeader("Content-Type", "application/json")
            .body("{\"messageId\":\"brevo-message-1\"}")
            .build());

        var result = provider.send(message());

        RecordedRequest request = server.takeRequest();
        JsonNode json = new ObjectMapper().readTree(request.getBody().utf8());
        assertThat(result.providerMessageId()).isEqualTo("brevo-message-1");
        assertThat(request.getUrl().encodedPath()).isEqualTo("/smtp/email");
        assertThat(request.getHeaders().get("api-key")).isEqualTo(API_KEY);
        assertThat(json.path("sender").path("email").asText()).isEqualTo("billing@example.com");
        assertThat(json.path("to").get(0).path("email").asText()).isEqualTo("customer@example.com");
        assertThat(json.path("cc").get(0).path("email").asText()).isEqualTo("accounting@example.com");
        assertThat(json.path("replyTo").path("email").asText()).isEqualTo("reply@example.com");
        assertThat(json.path("subject").asText()).isEqualTo("SF-2026-000123");
        assertThat(json.path("attachment").get(0).path("name").asText()).isEqualTo("invoice.pdf");
        assertThat(json.path("attachment").get(0).path("content").asText())
            .isEqualTo("cGRm");
    }

    @Test
    void mapsClientServerAndMalformedResponsesWithoutResponseBodyLeakage() {
        server.enqueue(new MockResponse.Builder()
            .code(400)
            .body("{\"message\":\"sensitive-provider-body\"}")
            .build());
        assertCode("EMAIL_PROVIDER_REJECTED");

        server.enqueue(new MockResponse.Builder()
            .code(503)
            .body("{\"message\":\"sensitive-provider-body\"}")
            .build());
        assertCode("EMAIL_PROVIDER_UNAVAILABLE");

        server.enqueue(new MockResponse.Builder()
            .code(201)
            .addHeader("Content-Type", "application/json")
            .body("not-json")
            .build());
        assertCode("EMAIL_PROVIDER_MALFORMED_RESPONSE");
    }

    @Test
    void mapsTimeoutAndNeverExposesSecretInConfigurationText() {
        server.enqueue(new MockResponse.Builder()
            .code(201)
            .addHeader("Content-Type", "application/json")
            .body("{\"messageId\":\"late\"}")
            .bodyDelay(2, TimeUnit.SECONDS)
            .build());

        assertCode("EMAIL_PROVIDER_TIMEOUT");
        assertThat(properties(Duration.ofSeconds(1)).toString()).doesNotContain(API_KEY);
    }

    private void assertCode(String code) {
        assertThatThrownBy(() -> provider.send(message()))
            .isInstanceOfSatisfying(EmailProviderException.class, exception -> {
                assertThat(exception.code()).isEqualTo(code);
                assertThat(exception.getMessage()).doesNotContain(
                    "sensitive-provider-body",
                    API_KEY
                );
            });
    }

    private EmailMessage message() {
        return new EmailMessage(
            List.of("customer@example.com"),
            List.of("accounting@example.com"),
            "SF-2026-000123",
            "Text",
            "<p>Text</p>",
            List.of(new EmailMessageAttachment(
                "invoice.pdf",
                "application/pdf",
                "pdf".getBytes(StandardCharsets.UTF_8)
            ))
        );
    }

    private EmailProperties properties(Duration readTimeout) {
        return new EmailProperties(
            true,
            EmailProviderType.BREVO,
            API_KEY,
            "billing@example.com",
            "Tillora",
            "reply@example.com",
            Duration.ofSeconds(1),
            readTimeout,
            10,
            DataSize.ofMegabytes(25)
        );
    }
}
