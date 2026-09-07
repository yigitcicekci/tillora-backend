package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.finance.api.request.CreateBankAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.BankAccountResponse;
import com.yigitcicekci.tillora.finance.application.service.BankAccountService;
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
import org.springframework.dao.DataIntegrityViolationException;
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
class BankAccountIntegrationTest {

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
    private BankAccountService bankAccountService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;
    private UUID actorId;

    @BeforeEach
    void createCompany() {
        company = companyRepository.saveAndFlush(company("30" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(company.id());
        userAccessSetupService.initializeForCompany(company.id());
        actorId = UUID.randomUUID();
    }

    @Test
    void createsPostingAccountUnderBanksAndBootstrapsLeastPrivilegePermissions() {
        BankAccountResponse response = bankAccountService.create(
            company.id(),
            actorId,
            new CreateBankAccountRequest(
                "  Main Bank  ",
                "  Istanbul Branch  ",
                "tr33 0006 1005 1978 6457 8413 26",
                " 12345678 ",
                null
            )
        );

        Map<String, Object> row = jdbcTemplate.queryForMap(
            """
            SELECT b.name,
                   b.branch,
                   b.iban,
                   b.account_number,
                   b.account_code,
                   b.currency,
                   b.active AS bank_active,
                   a.code AS ledger_code,
                   a.name AS ledger_name,
                   a.level,
                   a.category,
                   a.nature,
                   a.posting_allowed,
                   a.active AS ledger_active,
                   p.system_key AS parent_system_key
            FROM bank_accounts b
            JOIN chart_of_accounts a
              ON a.id = b.chart_of_account_id
             AND a.company_id = b.company_id
            JOIN chart_of_accounts p
              ON p.id = a.parent_id
             AND p.company_id = a.company_id
            WHERE b.id = ?
            """,
            response.id()
        );

        assertThat(response.name()).isEqualTo("Main Bank");
        assertThat(response.accountCode()).isEqualTo("102.01");
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.active()).isTrue();
        assertThat(row)
            .containsEntry("name", "Main Bank")
            .containsEntry("branch", "Istanbul Branch")
            .containsEntry("iban", "TR330006100519786457841326")
            .containsEntry("account_number", "12345678")
            .containsEntry("account_code", "102.01")
            .containsEntry("currency", "TRY")
            .containsEntry("bank_active", true)
            .containsEntry("ledger_code", "102.01")
            .containsEntry("ledger_name", "Main Bank")
            .containsEntry("level", 2)
            .containsEntry("category", "ASSET")
            .containsEntry("nature", "DEBIT")
            .containsEntry("posting_allowed", true)
            .containsEntry("ledger_active", true)
            .containsEntry("parent_system_key", "BANKS");
        assertThat(bankAccountService.get(company.id(), response.id()).id()).isEqualTo(response.id());
        assertThat(bankAccountService.list(company.id(), true, PageRequest.of(0, 20)).getContent())
            .extracting(BankAccountResponse::id)
            .containsExactly(response.id());
        assertThat(permissionCodes("ADMIN"))
            .contains("BANK_ACCOUNT_READ", "BANK_ACCOUNT_CREATE", "BANK_ACCOUNT_DISABLE");
        assertThat(permissionCodes("ACCOUNTING"))
            .contains("BANK_ACCOUNT_READ", "BANK_ACCOUNT_CREATE", "BANK_ACCOUNT_DISABLE");
        assertThat(permissionCodes("VIEWER"))
            .contains("BANK_ACCOUNT_READ")
            .doesNotContain("BANK_ACCOUNT_CREATE", "BANK_ACCOUNT_DISABLE");
        assertThat(permissionCodes("SALES"))
            .doesNotContain("BANK_ACCOUNT_READ", "BANK_ACCOUNT_CREATE", "BANK_ACCOUNT_DISABLE");
    }

    @Test
    void isolatesTenantsAndDisablesBankAndLedgerAccountsTogether() {
        Company otherCompany = companyRepository.saveAndFlush(company("31" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(otherCompany.id());
        BankAccountResponse created = bankAccountService.create(
            company.id(),
            actorId,
            new CreateBankAccountRequest(
                "Main Bank",
                "Istanbul Branch",
                "TR330006100519786457841326",
                "12345678",
                "USD"
            )
        );

        assertThatThrownBy(() -> bankAccountService.get(otherCompany.id(), created.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("BANK_ACCOUNT_NOT_FOUND");
        assertThatThrownBy(() -> bankAccountService.disable(otherCompany.id(), actorId, created.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("BANK_ACCOUNT_NOT_FOUND");

        BankAccountResponse disabled = bankAccountService.disable(company.id(), actorId, created.id());
        BankAccountResponse disabledAgain = bankAccountService.disable(company.id(), actorId, created.id());
        Map<String, Object> states = jdbcTemplate.queryForMap(
            """
            SELECT b.active AS bank_active,
                   a.active AS ledger_active
            FROM bank_accounts b
            JOIN chart_of_accounts a
              ON a.id = b.chart_of_account_id
             AND a.company_id = b.company_id
            WHERE b.id = ?
            """,
            created.id()
        );
        Integer auditCount = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM audit_logs
            WHERE company_id = ?
              AND entity_id = ?
              AND action IN ('BANK_ACCOUNT_CREATE', 'BANK_ACCOUNT_DISABLE')
            """,
            Integer.class,
            company.id(),
            created.id()
        );

        assertThat(disabled.active()).isFalse();
        assertThat(disabledAgain.active()).isFalse();
        assertThat(states)
            .containsEntry("bank_active", false)
            .containsEntry("ledger_active", false);
        assertThat(bankAccountService.list(company.id(), true, PageRequest.of(0, 20)).getContent()).isEmpty();
        assertThat(bankAccountService.list(company.id(), false, PageRequest.of(0, 20)).getContent())
            .extracting(BankAccountResponse::id)
            .containsExactly(created.id());
        assertThat(auditCount).isEqualTo(2);
    }

    @Test
    void databaseRejectsCrossCompanyChartAccountAssignment() {
        Company otherCompany = companyRepository.saveAndFlush(company("32" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(otherCompany.id());
        BankAccountResponse otherBank = bankAccountService.create(
            otherCompany.id(),
            actorId,
            new CreateBankAccountRequest(
                "Other Bank",
                "Ankara Branch",
                "TR760006200027100000006437",
                "87654321",
                "TRY"
            )
        );
        Map<String, Object> chartAccount = jdbcTemplate.queryForMap(
            """
            SELECT chart_of_account_id, account_code
            FROM bank_accounts
            WHERE id = ?
            """,
            otherBank.id()
        );

        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO bank_accounts (
                id,
                company_id,
                name,
                branch,
                iban,
                account_number,
                chart_of_account_id,
                account_code,
                currency
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            company.id(),
            "Cross Company Bank",
            "Izmir Branch",
            "TR680006200119000006672315",
            "99887766",
            chartAccount.get("chart_of_account_id"),
            chartAccount.get("account_code"),
            "TRY"
        )).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void concurrentCreatesReceiveSequentialBankCodes() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<BankAccountResponse>> futures = List.of(
                executor.submit(() -> createBankAccount(
                    ready,
                    start,
                    "Main Bank",
                    "TR330006100519786457841326"
                )),
                executor.submit(() -> createBankAccount(
                    ready,
                    start,
                    "Branch Bank",
                    "TR760006200027100000006437"
                ))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(futures.stream().map(this::result).map(BankAccountResponse::accountCode).toList())
                .containsExactlyInAnyOrder("102.01", "102.02");
        }

        assertThat(bankSequenceValue()).isEqualTo(2);
        assertThat(bankLedgerCount()).isEqualTo(2);
    }

    @Test
    void concurrentNormalizedDuplicateIbanRollsBackLedgerSequenceAndAudit() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                executor.submit(() -> createBankAccountOutcome(
                    ready,
                    start,
                    "Main Bank",
                    " TR33 0006 1005 1978 6457 8413 26 "
                )),
                executor.submit(() -> createBankAccountOutcome(
                    ready,
                    start,
                    "Branch Bank",
                    "tr330006100519786457841326"
                ))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = futures.stream().map(this::resultObject).toList();
        }

        assertThat(outcomes.stream().filter(BankAccountResponse.class::isInstance).toList()).hasSize(1);
        assertThat(outcomes.stream().filter(Throwable.class::isInstance).toList()).hasSize(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM bank_accounts WHERE company_id = ? AND iban = 'TR330006100519786457841326'",
            Integer.class,
            company.id()
        )).isEqualTo(1);
        assertThat(bankSequenceValue()).isEqualTo(1);
        assertThat(bankLedgerCount()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE company_id = ? AND action = 'BANK_ACCOUNT_CREATE'",
            Integer.class,
            company.id()
        )).isEqualTo(1);
    }

    private BankAccountResponse createBankAccount(
        CountDownLatch ready,
        CountDownLatch start,
        String name,
        String iban
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out.");
        }
        return bankAccountService.create(
            company.id(),
            actorId,
            new CreateBankAccountRequest(name, "Istanbul Branch", iban, "12345678", null)
        );
    }

    private Object createBankAccountOutcome(
        CountDownLatch ready,
        CountDownLatch start,
        String name,
        String iban
    ) throws InterruptedException {
        try {
            return createBankAccount(ready, start, name, iban);
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private BankAccountResponse result(Future<BankAccountResponse> future) {
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

    private int bankSequenceValue() {
        return jdbcTemplate.queryForObject(
            "SELECT current_value FROM account_code_sequences WHERE company_id = ? AND main_account_code = '102'",
            Integer.class,
            company.id()
        );
    }

    private int bankLedgerCount() {
        return jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM chart_of_accounts a
            JOIN chart_of_accounts p
              ON p.id = a.parent_id
             AND p.company_id = a.company_id
            WHERE a.company_id = ?
              AND p.system_key = 'BANKS'
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
            "Bank Test Company " + taxNumber,
            "Bank Test Company " + taxNumber,
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
