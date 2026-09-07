package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.finance.api.request.CreateCashAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.CashAccountResponse;
import com.yigitcicekci.tillora.finance.application.service.CashAccountService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import java.util.Currency;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest
class CashAccountIntegrationTest {

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

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private DefaultAccountingSetupService defaultAccountingSetupService;

    @Autowired
    private UserAccessSetupService userAccessSetupService;

    @Autowired
    private CashAccountService cashAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;
    private UUID actorId;

    @BeforeEach
    void createCompany() {
        company = companyRepository.saveAndFlush(company("20" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(company.id());
        userAccessSetupService.initializeForCompany(company.id());
        actorId = UUID.randomUUID();
    }

    @Test
    void createsPostingAccountUnderCashAndBootstrapsLeastPrivilegePermissions() {
        CashAccountResponse response = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("  Main Cash  ", null)
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
            """
            SELECT c.name,
                   c.account_code,
                   c.currency,
                   c.active AS cash_active,
                   a.code AS ledger_code,
                   a.name AS ledger_name,
                   a.level,
                   a.category,
                   a.nature,
                   a.posting_allowed,
                   a.active AS ledger_active,
                   p.system_key AS parent_system_key
            FROM cash_accounts c
            JOIN chart_of_accounts a
              ON a.id = c.chart_of_account_id
             AND a.company_id = c.company_id
            JOIN chart_of_accounts p
              ON p.id = a.parent_id
             AND p.company_id = a.company_id
            WHERE c.id = ?
            """,
            response.id()
        );

        assertThat(response.name()).isEqualTo("Main Cash");
        assertThat(response.accountCode()).isEqualTo("100.01");
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.active()).isTrue();
        assertThat(row)
            .containsEntry("name", "Main Cash")
            .containsEntry("account_code", "100.01")
            .containsEntry("currency", "TRY")
            .containsEntry("cash_active", true)
            .containsEntry("ledger_code", "100.01")
            .containsEntry("ledger_name", "Main Cash")
            .containsEntry("level", 2)
            .containsEntry("category", "ASSET")
            .containsEntry("nature", "DEBIT")
            .containsEntry("posting_allowed", true)
            .containsEntry("ledger_active", true)
            .containsEntry("parent_system_key", "CASH");
        assertThat(cashAccountService.get(company.id(), response.id()).id()).isEqualTo(response.id());
        assertThat(cashAccountService.list(company.id(), true, PageRequest.of(0, 20)).getContent())
            .extracting(CashAccountResponse::id)
            .containsExactly(response.id());
        assertThat(permissionCodes("ADMIN"))
            .contains("CASH_ACCOUNT_READ", "CASH_ACCOUNT_CREATE", "CASH_ACCOUNT_DISABLE");
        assertThat(permissionCodes("ACCOUNTING"))
            .contains("CASH_ACCOUNT_READ", "CASH_ACCOUNT_CREATE", "CASH_ACCOUNT_DISABLE");
        assertThat(permissionCodes("VIEWER"))
            .contains("CASH_ACCOUNT_READ")
            .doesNotContain("CASH_ACCOUNT_CREATE", "CASH_ACCOUNT_DISABLE");
        assertThat(permissionCodes("SALES"))
            .doesNotContain("CASH_ACCOUNT_READ", "CASH_ACCOUNT_CREATE", "CASH_ACCOUNT_DISABLE");
    }

    @Test
    void isolatesTenantsAndDisablesCashAndLedgerAccountsTogether() {
        Company otherCompany = companyRepository.saveAndFlush(company("21" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(otherCompany.id());
        CashAccountResponse created = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Main Cash", "USD")
        );

        assertThatThrownBy(() -> cashAccountService.get(otherCompany.id(), created.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("CASH_ACCOUNT_NOT_FOUND");
        assertThatThrownBy(() -> cashAccountService.disable(otherCompany.id(), actorId, created.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("CASH_ACCOUNT_NOT_FOUND");

        CashAccountResponse disabled = cashAccountService.disable(company.id(), actorId, created.id());
        Map<String, Object> states = jdbcTemplate.queryForMap(
            """
            SELECT c.active AS cash_active,
                   a.active AS ledger_active
            FROM cash_accounts c
            JOIN chart_of_accounts a
              ON a.id = c.chart_of_account_id
             AND a.company_id = c.company_id
            WHERE c.id = ?
            """,
            created.id()
        );
        Integer auditCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM audit_logs
            WHERE company_id = ?
              AND entity_id = ?
              AND action IN ('CASH_ACCOUNT_CREATE', 'CASH_ACCOUNT_DISABLE')
            """,
            Integer.class,
            company.id(),
            created.id()
        );

        assertThat(disabled.active()).isFalse();
        assertThat(states)
            .containsEntry("cash_active", false)
            .containsEntry("ledger_active", false);
        assertThat(cashAccountService.list(company.id(), true, PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(cashAccountService.list(company.id(), false, PageRequest.of(0, 20)).getContent())
            .extracting(CashAccountResponse::id)
            .containsExactly(created.id());
        assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void concurrentCreatesReceiveSequentialCashCodes() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<CashAccountResponse>> futures = List.of(
                executor.submit(() -> createCashAccount(ready, start, "Main Cash")),
                executor.submit(() -> createCashAccount(ready, start, "Branch Cash"))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(futures.stream().map(this::result).map(CashAccountResponse::accountCode).toList())
                .containsExactlyInAnyOrder("100.01", "100.02");
        }

        assertThat(cashSequenceValue()).isEqualTo(2);
        assertThat(cashLedgerCount()).isEqualTo(2);
    }

    @Test
    void concurrentNormalizedDuplicateRollsBackLedgerSequenceAndAudit() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                executor.submit(() -> createCashAccountOutcome(ready, start, " Main Cash ")),
                executor.submit(() -> createCashAccountOutcome(ready, start, "main cash"))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = futures.stream().map(this::resultObject).toList();
        }

        assertThat(outcomes.stream().filter(CashAccountResponse.class::isInstance).toList()).hasSize(1);
        assertThat(outcomes.stream().filter(Throwable.class::isInstance).toList()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM cash_accounts WHERE company_id = ? AND lower(name) = 'main cash'",
            Integer.class,
            company.id()
        )).isEqualTo(1);
        assertThat(cashSequenceValue()).isEqualTo(1);
        assertThat(cashLedgerCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE company_id = ? AND action = 'CASH_ACCOUNT_CREATE'",
            Integer.class,
            company.id()
        )).isEqualTo(1);
    }

    private CashAccountResponse createCashAccount(
        CountDownLatch ready,
        CountDownLatch start,
        String name
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out.");
        }
        return cashAccountService.create(company.id(), actorId, new CreateCashAccountRequest(name, null));
    }

    private Object createCashAccountOutcome(
        CountDownLatch ready,
        CountDownLatch start,
        String name
    ) throws InterruptedException {
        try {
            return createCashAccount(ready, start, name);
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private CashAccountResponse result(Future<CashAccountResponse> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private Object resultObject(Future<Object> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private int cashSequenceValue() {
        return jdbcTemplate.queryForObject(
            "SELECT current_value FROM account_code_sequences WHERE company_id = ? AND main_account_code = '100'",
            Integer.class,
            company.id()
        );
    }

    private int cashLedgerCount() {
        return jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM chart_of_accounts a
            JOIN chart_of_accounts p
              ON p.id = a.parent_id
             AND p.company_id = a.company_id
            WHERE a.company_id = ?
              AND p.system_key = 'CASH'
            """,
            Integer.class,
            company.id()
        );
    }

    private List<String> permissionCodes(String roleName) {
        return jdbcTemplate.queryForList(
            """
            SELECT p.code
            FROM roles r
            JOIN role_permissions rp ON rp.role_id = r.id
            JOIN permissions p ON p.id = rp.permission_id
            WHERE r.company_id = ?
              AND r.name = ?
            ORDER BY p.code
            """,
            String.class,
            company.id(),
            roleName
        );
    }

    private Company company(String source) {
        String digits = source.replaceAll("\\D", "");
        String taxNumber = (digits + "0000000000").substring(0, 10);
        return Company.create(
            "Cash Test Company " + taxNumber,
            "Cash Test Company " + taxNumber,
            taxNumber,
            null,
            null,
            null,
            null,
            Currency.getInstance("TRY"),
            "Europe/Istanbul"
        );
    }
}
