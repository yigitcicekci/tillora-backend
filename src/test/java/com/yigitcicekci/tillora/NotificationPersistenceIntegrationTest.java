package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.currentaccount.api.request.CreateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import com.yigitcicekci.tillora.notification.application.service.EmailDeliveryStateService;
import com.yigitcicekci.tillora.notification.application.service.PreparedEmailDelivery;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailAttachmentType;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryReferenceType;
import com.yigitcicekci.tillora.notification.domain.repository.EmailDeliveryRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest
class NotificationPersistenceIntegrationTest {

    static final GenericContainer<?> REDIS = new GenericContainer<>(
        DockerImageName.parse("redis:7.4-alpine")
    ).withExposedPorts(6379);
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
        DockerImageName.parse("postgres:17")
    );

    @DynamicPropertySource
    static void infrastructureProperties(DynamicPropertyRegistry registry) {
        if (Boolean.getBoolean("tillora.test.external-services")) {
            registry.add("spring.datasource.url", () -> System.getProperty(
                "tillora.test.postgres-url",
                "jdbc:postgresql://127.0.0.1:55432/tillora"
            ));
            registry.add("spring.datasource.username", () -> "tillora");
            registry.add("spring.datasource.password", () -> "");
            registry.add("spring.data.redis.host", () -> "127.0.0.1");
            registry.add(
                "spring.data.redis.port",
                () -> Integer.getInteger("tillora.test.redis-port", 56379)
            );
            return;
        }
        POSTGRES.start();
        REDIS.start();
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired private CompanyRepository companyRepository;
    @Autowired private DefaultAccountingSetupService accountingSetupService;
    @Autowired private UserAccessSetupService accessSetupService;
    @Autowired private CurrentAccountService currentAccountService;
    @Autowired private EmailDeliveryStateService stateService;
    @Autowired private EmailDeliveryRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    private Company company;
    private UUID actorId;
    private UUID currentAccountId;

    @BeforeEach
    void setUp() {
        company = createCompany();
        actorId = insertUser(company.id());
        currentAccountId = currentAccountService.create(
            company.id(),
            actorId,
            new CreateCurrentAccountRequest(
                "Notification Customer",
                null,
                "9200000001",
                null,
                null,
                null,
                "customer@example.com",
                null,
                TradeType.RETAIL,
                RelationshipType.CUSTOMER
            )
        ).id();
    }

    @Test
    void persistsIdempotentStatementDeliveryAndRejectsKeyReuse() {
        PreparedEmailDelivery created = prepare("key-1", "a".repeat(64));
        PreparedEmailDelivery repeated = prepare("key-1", "a".repeat(64));

        assertThat(repeated.created()).isFalse();
        assertThat(repeated.delivery().id()).isEqualTo(created.delivery().id());
        assertThat(repository.findById(created.delivery().id())).isPresent();
        assertThatThrownBy(() -> prepare("key-1", "b".repeat(64)))
            .isInstanceOfSatisfying(BusinessException.class, exception ->
                assertThat(exception.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED")
            );
    }

    @Test
    void storesFailureAndScopesReferenceByCompany() {
        PreparedEmailDelivery created = prepare("key-failure", "c".repeat(64));

        stateService.failed(
            company.id(),
            created.delivery().id(),
            "EMAIL_PROVIDER_UNAVAILABLE",
            "Email provider is unavailable."
        );

        assertThat(repository.findById(created.delivery().id()).orElseThrow().status().name())
            .isEqualTo("FAILED");
        assertThat(jdbcTemplate.queryForList(
            "SELECT action FROM audit_logs WHERE entity_id = ? ORDER BY created_at",
            String.class,
            created.delivery().id()
        )).containsExactly("EMAIL_DELIVERY_REQUESTED", "EMAIL_DELIVERY_FAILED");

        Company other = createCompany();
        assertThat(repository.findByCompanyIdAndReferenceTypeAndReferenceIdAndIdempotencyKey(
            other.id(),
            EmailDeliveryReferenceType.CURRENT_ACCOUNT_STATEMENT,
            currentAccountId,
            "key-failure"
        )).isEmpty();
    }

    private PreparedEmailDelivery prepare(String key, String fingerprint) {
        return stateService.prepareCurrentAccountStatement(
            company.id(),
            actorId,
            currentAccountId,
            key,
            fingerprint,
            "Cari hesap ekstresi",
            List.of("customer@example.com"),
            List.of(),
            List.of(EmailAttachmentType.STATEMENT_CSV)
        );
    }

    private Company createCompany() {
        String taxNumber = Long.toString(Math.abs(System.nanoTime()));
        taxNumber = (taxNumber + "0000000000").substring(0, 10);
        Company created = companyRepository.saveAndFlush(Company.create(
            "Notification Test " + taxNumber,
            "Notification Test " + taxNumber,
            taxNumber,
            null,
            null,
            null,
            null,
            Currency.getInstance("TRY"),
            "Europe/Istanbul"
        ));
        accountingSetupService.initializeForCompany(created.id());
        accessSetupService.initializeForCompany(created.id());
        return created;
    }

    private UUID insertUser(UUID companyId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO users (
                id, company_id, username, email, password_hash,
                first_name, last_name, status, must_change_password
            )
            VALUES (?, ?, ?, ?, ?, 'Email', 'User', 'ACTIVE', false)
            """,
            id,
            companyId,
            "email-user-" + id,
            id + "@example.com",
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ12345"
        );
        return id;
    }
}
