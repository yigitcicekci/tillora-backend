package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;

import com.yigitcicekci.tillora.audit.infrastructure.persistence.AuditQueryRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = "management.server.port=0"
)
class ManagementSecurityIntegrationTest {

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
        .withExposedPorts(6379);
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(DockerImageName.parse("postgres:17"));

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        POSTGRES.start();
        REDIS.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @LocalManagementPort
    private int managementPort;

    @Autowired
    private AuditQueryRepository auditQueryRepository;

    @Test
    void protectsMetricsWithDedicatedBasicAuthentication() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        auditQueryRepository.findAll(
            UUID.randomUUID(),
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            null,
            null,
            null,
            PageRequest.of(0, 1)
        );
        String basicAuth = "Basic " + Base64.getEncoder().encodeToString(
            "tillora-monitoring:test-only-management-password".getBytes(StandardCharsets.UTF_8)
        );

        assertThat(status(client, "/actuator/health/readiness", null)).isEqualTo(200);
        assertThat(status(client, "/actuator/prometheus", null)).isEqualTo(401);
        assertThat(status(client, "/actuator/metrics", "Bearer business-user-token")).isEqualTo(401);
        assertThat(status(client, "/actuator/prometheus", basicAuth)).isEqualTo(200);
        assertThat(body(client, "/actuator/prometheus", basicAuth))
            .contains("tillora_database_query_seconds_count");
    }

    private int status(HttpClient client, String path, String authorization) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + managementPort + path))
            .GET();
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.discarding()).statusCode();
    }

    private String body(HttpClient client, String path, String authorization) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create("http://127.0.0.1:" + managementPort + path))
            .header("Authorization", authorization)
            .GET()
            .build();
        return client.send(request, HttpResponse.BodyHandlers.ofString()).body();
    }
}
