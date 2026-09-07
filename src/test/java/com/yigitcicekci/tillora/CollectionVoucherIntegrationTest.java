package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.currentaccount.api.request.CreateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.request.UpdateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.response.CurrentAccountResponse;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import com.yigitcicekci.tillora.finance.api.request.CreateBankAccountRequest;
import com.yigitcicekci.tillora.finance.api.request.CreateCashAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.BankAccountResponse;
import com.yigitcicekci.tillora.finance.api.response.CashAccountResponse;
import com.yigitcicekci.tillora.finance.application.service.BankAccountService;
import com.yigitcicekci.tillora.finance.application.service.CashAccountService;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardSummary;
import com.yigitcicekci.tillora.dashboard.application.service.DashboardService;
import com.yigitcicekci.tillora.reporting.application.service.ReportingService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import com.yigitcicekci.tillora.voucher.api.request.CreateCollectionVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.UpdateVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.VoucherLineRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.application.service.CollectionVoucherService;
import com.yigitcicekci.tillora.voucher.application.service.VoucherService;
import com.yigitcicekci.tillora.voucher.domain.enumeration.SettlementAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
import org.springframework.data.domain.PageRequest;
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
class CollectionVoucherIntegrationTest {

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
    private CurrentAccountService currentAccountService;

    @Autowired
    private CashAccountService cashAccountService;

    @Autowired
    private BankAccountService bankAccountService;

    @Autowired
    private CollectionVoucherService collectionVoucherService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ReportingService reportingService;

    @Autowired
    private DashboardService dashboardService;

    private Company company;
    private UUID actorId;
    private CurrentAccountResponse customer;
    private CashAccountResponse cashAccount;
    private BankAccountResponse bankAccount;

    @BeforeEach
    void createFinancialSetup() {
        company = companyRepository.saveAndFlush(company("50" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(company.id());
        userAccessSetupService.initializeForCompany(company.id());
        actorId = insertUser(company.id());
        customer = currentAccountService.create(
            company.id(),
            actorId,
            currentAccount("Customer", "5100000000", RelationshipType.CUSTOMER)
        );
        cashAccount = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Main Cash", null)
        );
        bankAccount = bankAccountService.create(
            company.id(),
            actorId,
            new CreateBankAccountRequest(
                "Main Bank",
                "Istanbul",
                "TR330006100519786457841326",
                "12345678",
                null
            )
        );
    }

    @Test
    void createsApprovesAndReturnsIdempotentCashCollection() {
        UUID idempotencyKey = UUID.randomUUID();
        CreateCollectionVoucherRequest request = request(
            idempotencyKey,
            customer.id(),
            SettlementAccountType.CASH,
            cashAccount.id(),
            "1000"
        );

        VoucherResponse created = collectionVoucherService.create(company.id(), actorId, request);
        VoucherResponse repeated = collectionVoucherService.create(company.id(), actorId, request);

        assertThat(created.id()).isEqualTo(repeated.id());
        assertThat(created.voucherNumber()).isEqualTo("THS-2026-000001");
        assertThat(created.voucherType()).isEqualTo(VoucherType.COLLECTION);
        assertThat(created.status()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(created.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(created.totalDebit()).isEqualByComparingTo("1000.0000");
        assertThat(created.totalCredit()).isEqualByComparingTo("1000.0000");
        assertThat(created.lines()).extracting(line -> line.accountCode())
            .containsExactly(cashAccount.accountCode(), customer.ledgerAccounts().getFirst().fullAccountCode());
        assertThat(created.lines().getFirst().debit()).isEqualByComparingTo("1000.0000");
        assertThat(created.lines().getFirst().currentAccountId()).isNull();
        assertThat(created.lines().getLast().credit()).isEqualByComparingTo("1000.0000");
        assertThat(created.lines().getLast().currentAccountId()).isEqualTo(customer.id());
        assertThat(voucherCount(idempotencyKey)).isEqualTo(1);
        assertThat(auditCount(created.id(), "VOUCHER_CREATE")).isEqualTo(1);

        assertThatThrownBy(() -> voucherService.update(
            company.id(),
            actorId,
            created.id(),
            new UpdateVoucherRequest(
                LocalDate.of(2026, 7, 16),
                "Manual replacement",
                null,
                null,
                List.of(
                    new VoucherLineRequest(
                        UUID.randomUUID(),
                        null,
                        new BigDecimal("1000"),
                        BigDecimal.ZERO,
                        null,
                        null
                    ),
                    new VoucherLineRequest(
                        UUID.randomUUID(),
                        null,
                        BigDecimal.ZERO,
                        new BigDecimal("1000"),
                        null,
                        null
                    )
                )
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("GUIDED_VOUCHER_CANNOT_BE_MANUALLY_UPDATED");

        VoucherResponse approved = voucherService.approve(company.id(), actorId, created.id());
        assertThat(approved.status()).isEqualTo(VoucherStatus.APPROVED);
        assertThat(auditCount(created.id(), "VOUCHER_APPROVE")).isEqualTo(1);
    }

    @Test
    void createsOptionalCurrentAccountOpeningBalanceMovement() {
        CurrentAccountResponse openingCustomer = currentAccountService.create(
            company.id(),
            actorId,
            new CreateCurrentAccountRequest(
                "Opening Customer",
                null,
                "5400000000",
                null,
                null,
                null,
                null,
                null,
                TradeType.RETAIL,
                RelationshipType.CUSTOMER,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("125.5000"),
                null
            )
        );

        Map<String, Object> movement = jdbcTemplate.queryForMap(
            """
                SELECT movement_type, reference_type, reference_id, debit, credit
                FROM current_account_movements
                WHERE company_id = ? AND current_account_id = ?
                """,
            company.id(),
            openingCustomer.id()
        );

        assertThat(movement)
            .containsEntry("movement_type", "OPENING_BALANCE")
            .containsEntry("reference_type", "CURRENT_ACCOUNT")
            .containsEntry("reference_id", openingCustomer.id())
            .containsEntry("debit", new BigDecimal("125.5000"))
            .containsEntry("credit", new BigDecimal("0.0000"));

        CurrentAccountResponse openingSupplier = currentAccountService.create(
            company.id(),
            actorId,
            new CreateCurrentAccountRequest(
                "Opening Supplier",
                null,
                "5500000000",
                null,
                null,
                null,
                null,
                null,
                TradeType.RETAIL,
                RelationshipType.SUPPLIER,
                null,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("75.0000")
            )
        );
        assertThat(jdbcTemplate.queryForObject(
            "SELECT credit FROM current_account_movements WHERE company_id = ? AND current_account_id = ?",
            BigDecimal.class,
            company.id(),
            openingSupplier.id()
        )).isEqualByComparingTo("75.0000");

        assertThat(reportingService.currentAccountStatement(
            company.id(),
            openingCustomer.id(),
            LocalDate.of(2000, 1, 1),
            LocalDate.of(9999, 12, 31),
            PageRequest.of(0, 20)
        ))
            .singleElement()
            .satisfies(row -> {
                assertThat(row.voucherType()).isEqualTo("Opening balance");
                assertThat(row.movementNote()).isEqualTo("Opening balance");
                assertThat(row.debit()).isEqualByComparingTo("125.5000");
                assertThat(row.balance()).isEqualByComparingTo("125.5000");
            });
        assertThat(reportingService.receivables(
            company.id(),
            LocalDate.of(2000, 1, 1),
            LocalDate.of(9999, 12, 31),
            PageRequest.of(0, 20)
        ))
            .singleElement()
            .satisfies(row -> assertThat(row.balance()).isEqualByComparingTo("125.5000"));
        assertThat(reportingService.payables(
            company.id(),
            LocalDate.of(2000, 1, 1),
            LocalDate.of(9999, 12, 31),
            PageRequest.of(0, 20)
        ))
            .singleElement()
            .satisfies(row -> assertThat(row.balance()).isEqualByComparingTo("75.0000"));

        DashboardSummary dashboard = dashboardService.summary(company.id(), YearMonth.of(2026, 8));
        assertThat(dashboard.cards().totalReceivables()).isEqualByComparingTo("125.5000");
        assertThat(dashboard.cards().totalPayables()).isEqualByComparingTo("75.0000");
    }

    @Test
    void concurrentIdenticalBankCollectionsCreateOneVoucherAndOneNumber() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        CreateCollectionVoucherRequest request = request(
            idempotencyKey,
            customer.id(),
            SettlementAccountType.BANK,
            bankAccount.id(),
            "750"
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<VoucherResponse>> futures = List.of(
                executor.submit(() -> createConcurrent(ready, start, request)),
                executor.submit(() -> createConcurrent(ready, start, request))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            List<VoucherResponse> responses = futures.stream().map(this::result).toList();
            assertThat(responses).extracting(VoucherResponse::id).containsOnly(responses.getFirst().id());
            assertThat(responses).extracting(VoucherResponse::voucherNumber)
                .containsOnly("THS-2026-000001");
            assertThat(responses.getFirst().lines().getFirst().accountCode()).isEqualTo(bankAccount.accountCode());
        }

        assertThat(voucherCount(idempotencyKey)).isEqualTo(1);
        assertThat(sequenceValue()).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE company_id = ? AND action = 'VOUCHER_CREATE'",
            Integer.class,
            company.id()
        )).isEqualTo(1);
    }

    @Test
    void rejectsSupplierForeignCurrencyAndReusedIdempotencyKeyBeforeNumbering() {
        CurrentAccountResponse supplier = currentAccountService.create(
            company.id(),
            actorId,
            currentAccount("Supplier", "5200000000", RelationshipType.SUPPLIER)
        );
        UUID supplierKey = UUID.randomUUID();

        assertThatThrownBy(() -> collectionVoucherService.create(
            company.id(),
            actorId,
            request(supplierKey, supplier.id(), SettlementAccountType.CASH, cashAccount.id(), "100")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("COLLECTION_CURRENT_ACCOUNT_INVALID");

        CashAccountResponse dollarCash = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Dollar Cash", "USD")
        );
        UUID currencyKey = UUID.randomUUID();
        assertThatThrownBy(() -> collectionVoucherService.create(
            company.id(),
            actorId,
            request(currencyKey, customer.id(), SettlementAccountType.CASH, dollarCash.id(), "100")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("COLLECTION_ACCOUNT_CURRENCY_MISMATCH");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM voucher_number_sequences WHERE company_id = ? AND voucher_type = 'COLLECTION'",
            Integer.class,
            company.id()
        )).isZero();

        UUID reusedKey = UUID.randomUUID();
        collectionVoucherService.create(
            company.id(),
            actorId,
            request(reusedKey, customer.id(), SettlementAccountType.CASH, cashAccount.id(), "100")
        );
        assertThatThrownBy(() -> collectionVoucherService.create(
            company.id(),
            actorId,
            request(reusedKey, customer.id(), SettlementAccountType.CASH, cashAccount.id(), "101")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(voucherCount(reusedKey)).isEqualTo(1);
        assertThat(sequenceValue()).isEqualTo(1);
    }

    @Test
    void deletesOnlyCurrentAccountsWithoutFinancialOrDocumentRecords() {
        CurrentAccountResponse unused = currentAccountService.create(
            company.id(),
            actorId,
            currentAccount("Unused Customer", "5300000000", RelationshipType.CUSTOMER)
        );
        UUID chartOfAccountId = unused.ledgerAccounts().getFirst().chartOfAccountId();

        currentAccountService.delete(company.id(), actorId, unused.id());

        assertThatThrownBy(() -> currentAccountService.get(company.id(), unused.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("CURRENT_ACCOUNT_NOT_FOUND");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM chart_of_accounts WHERE id = ?",
            Integer.class,
            chartOfAccountId
        )).isZero();
        assertThat(auditCount(unused.id(), "CURRENT_ACCOUNT_DELETE")).isEqualTo(1);

        collectionVoucherService.create(
            company.id(),
            actorId,
            request(UUID.randomUUID(), customer.id(), SettlementAccountType.CASH, cashAccount.id(), "100")
        );

        assertThatThrownBy(() -> currentAccountService.delete(company.id(), actorId, customer.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("CURRENT_ACCOUNT_IN_USE");
        assertThat(currentAccountService.get(company.id(), customer.id()).id()).isEqualTo(customer.id());
    }

    @Test
    void updatesCurrentAccountAndPostingAccountNameWithoutDuplicateWrites() {
        UpdateCurrentAccountRequest request = new UpdateCurrentAccountRequest(
            "Updated Customer",
            customer.legalName(),
            customer.taxNumber(),
            customer.taxOffice(),
            customer.identityNumber(),
            "5551234567",
            customer.email(),
            customer.address(),
            customer.district(),
            customer.city(),
            customer.postalCode(),
            customer.countryCode(),
            customer.countryName()
        );

        CurrentAccountResponse updated = currentAccountService.update(company.id(), actorId, customer.id(), request);
        currentAccountService.update(company.id(), actorId, customer.id(), request);

        assertThat(updated.name()).isEqualTo("Updated Customer");
        assertThat(updated.phone()).isEqualTo("5551234567");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT name FROM chart_of_accounts WHERE id = ?",
            String.class,
            customer.ledgerAccounts().getFirst().chartOfAccountId()
        )).isEqualTo("Updated Customer");
        assertThat(auditCount(customer.id(), "CURRENT_ACCOUNT_UPDATE")).isEqualTo(1);
    }

    private VoucherResponse createConcurrent(
        CountDownLatch ready,
        CountDownLatch start,
        CreateCollectionVoucherRequest request
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out.");
        }
        return collectionVoucherService.create(company.id(), actorId, request);
    }

    private VoucherResponse result(Future<VoucherResponse> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private CreateCollectionVoucherRequest request(
        UUID idempotencyKey,
        UUID currentAccountId,
        SettlementAccountType type,
        UUID settlementAccountId,
        String amount
    ) {
        return new CreateCollectionVoucherRequest(
            idempotencyKey,
            currentAccountId,
            type,
            settlementAccountId,
            new BigDecimal(amount),
            LocalDate.of(2026, 7, 16),
            "Customer collection",
            "RCPT-1"
        );
    }

    private CreateCurrentAccountRequest currentAccount(
        String name,
        String taxNumber,
        RelationshipType relationshipType
    ) {
        return new CreateCurrentAccountRequest(
            name,
            null,
            taxNumber,
            null,
            null,
            null,
            null,
            null,
            TradeType.RETAIL,
            relationshipType
        );
    }

    private int voucherCount(UUID idempotencyKey) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vouchers WHERE company_id = ? AND idempotency_key = ?",
            Integer.class,
            company.id(),
            idempotencyKey
        );
    }

    private int auditCount(UUID voucherId, String action) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE company_id = ? AND entity_id = ? AND action = ?",
            Integer.class,
            company.id(),
            voucherId,
            action
        );
    }

    private int sequenceValue() {
        return jdbcTemplate.queryForObject(
            """
            SELECT current_value
            FROM voucher_number_sequences
            WHERE company_id = ?
              AND voucher_type = 'COLLECTION'
              AND voucher_year = 2026
            """,
            Integer.class,
            company.id()
        );
    }

    private UUID insertUser(UUID companyId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO users (
                id,
                company_id,
                username,
                email,
                password_hash,
                first_name,
                last_name,
                status,
                must_change_password
            )
            VALUES (?, ?, ?, ?, ?, 'Collection', 'User', 'ACTIVE', false)
            """,
            id,
            companyId,
            "collection-user-" + id,
            id + "@example.com",
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ12345"
        );
        return id;
    }

    private Company company(String source) {
        String digits = source.replaceAll("\\D", "");
        String taxNumber = (digits + "0000000000").substring(0, 10);
        return Company.create(
            "Collection Test Company " + taxNumber,
            "Collection Test Company " + taxNumber,
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
