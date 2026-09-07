package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.currentaccount.api.request.CreateCurrentAccountRequest;
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
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import com.yigitcicekci.tillora.voucher.api.request.CreateCollectionVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.CreatePaymentVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.application.service.CollectionVoucherService;
import com.yigitcicekci.tillora.voucher.application.service.PaymentVoucherService;
import com.yigitcicekci.tillora.voucher.application.service.VoucherService;
import com.yigitcicekci.tillora.voucher.domain.enumeration.SettlementAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
class PaymentVoucherIntegrationTest {

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
    private PaymentVoucherService paymentVoucherService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;
    private UUID actorId;
    private CurrentAccountResponse supplier;
    private CashAccountResponse cashAccount;
    private BankAccountResponse bankAccount;

    @BeforeEach
    void createFinancialSetup() {
        company = companyRepository.saveAndFlush(company("60" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(company.id());
        userAccessSetupService.initializeForCompany(company.id());
        actorId = insertUser(company.id());
        supplier = currentAccountService.create(
            company.id(),
            actorId,
            currentAccount("Supplier", "6100000000", RelationshipType.SUPPLIER)
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
    void createsApprovesAndReturnsIdempotentCashPayment() {
        CurrentAccountResponse customer = currentAccountService.create(
            company.id(),
            actorId,
            currentAccount("Opening Customer", "6200000000", RelationshipType.CUSTOMER)
        );
        VoucherResponse openingCollection = collectionVoucherService.create(
            company.id(),
            actorId,
            new CreateCollectionVoucherRequest(
                UUID.randomUUID(),
                customer.id(),
                SettlementAccountType.CASH,
                cashAccount.id(),
                new BigDecimal("1000"),
                LocalDate.of(2026, 7, 16),
                "Opening cash balance",
                "OPEN-1"
            )
        );
        voucherService.approve(company.id(), actorId, openingCollection.id());

        UUID idempotencyKey = UUID.randomUUID();
        CreatePaymentVoucherRequest request = request(
            idempotencyKey,
            supplier.id(),
            SettlementAccountType.CASH,
            cashAccount.id(),
            "800"
        );

        VoucherResponse created = paymentVoucherService.create(company.id(), actorId, request);
        VoucherResponse repeated = paymentVoucherService.create(company.id(), actorId, request);

        assertThat(created.id()).isEqualTo(repeated.id());
        assertThat(created.voucherNumber()).isEqualTo("TDI-2026-000001");
        assertThat(created.voucherType()).isEqualTo(VoucherType.PAYMENT);
        assertThat(created.status()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(created.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(created.totalDebit()).isEqualByComparingTo("800.0000");
        assertThat(created.totalCredit()).isEqualByComparingTo("800.0000");
        assertThat(created.lines()).extracting(line -> line.accountCode())
            .containsExactly(supplier.ledgerAccounts().getFirst().fullAccountCode(), cashAccount.accountCode());
        assertThat(created.lines().getFirst().debit()).isEqualByComparingTo("800.0000");
        assertThat(created.lines().getFirst().currentAccountId()).isEqualTo(supplier.id());
        assertThat(created.lines().getLast().credit()).isEqualByComparingTo("800.0000");
        assertThat(created.lines().getLast().currentAccountId()).isNull();
        assertThat(voucherCount(idempotencyKey)).isEqualTo(1);
        assertThat(auditCount(created.id(), "VOUCHER_CREATE")).isEqualTo(1);

        VoucherResponse approved = voucherService.approve(company.id(), actorId, created.id());
        assertThat(approved.status()).isEqualTo(VoucherStatus.APPROVED);
        assertThat(auditCount(created.id(), "VOUCHER_APPROVE")).isEqualTo(1);
    }

    @Test
    void rejectsCashPaymentThatWouldMakeTheCashBalanceNegative() {
        VoucherResponse created = paymentVoucherService.create(
            company.id(),
            actorId,
            request(
                UUID.randomUUID(),
                supplier.id(),
                SettlementAccountType.CASH,
                cashAccount.id(),
                "800"
            )
        );

        assertThatThrownBy(() -> voucherService.approve(company.id(), actorId, created.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("CASH_ACCOUNT_BALANCE_INSUFFICIENT");
        assertThat(voucherService.get(company.id(), created.id()).status()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(auditCount(created.id(), "VOUCHER_APPROVE")).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vouchers WHERE company_id = ? AND status = 'APPROVED'",
            Integer.class,
            company.id()
        )).isZero();
    }

    @Test
    void serializesConcurrentCashPaymentsAgainstTheSameBalance() throws Exception {
        CurrentAccountResponse customer = currentAccountService.create(
            company.id(),
            actorId,
            currentAccount("Opening Customer", "6200000000", RelationshipType.CUSTOMER)
        );
        VoucherResponse openingCollection = collectionVoucherService.create(
            company.id(),
            actorId,
            new CreateCollectionVoucherRequest(
                UUID.randomUUID(),
                customer.id(),
                SettlementAccountType.CASH,
                cashAccount.id(),
                new BigDecimal("800"),
                LocalDate.of(2026, 7, 16),
                "Opening cash balance",
                "OPEN-1"
            )
        );
        voucherService.approve(company.id(), actorId, openingCollection.id());
        VoucherResponse first = paymentVoucherService.create(
            company.id(),
            actorId,
            request(UUID.randomUUID(), supplier.id(), SettlementAccountType.CASH, cashAccount.id(), "500")
        );
        VoucherResponse second = paymentVoucherService.create(
            company.id(),
            actorId,
            request(UUID.randomUUID(), supplier.id(), SettlementAccountType.CASH, cashAccount.id(), "500")
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<String>> futures = List.of(
                executor.submit(() -> approveConcurrent(ready, start, first.id())),
                executor.submit(() -> approveConcurrent(ready, start, second.id()))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(futures.stream().map(this::approvalResult).toList())
                .containsExactlyInAnyOrder("APPROVED", "CASH_ACCOUNT_BALANCE_INSUFFICIENT");
        }
    }

    @Test
    void concurrentIdenticalBankPaymentsCreateOneVoucherAndOneNumber() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        CreatePaymentVoucherRequest request = request(
            idempotencyKey,
            supplier.id(),
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
                .containsOnly("TDI-2026-000001");
            assertThat(responses.getFirst().lines().getLast().accountCode()).isEqualTo(bankAccount.accountCode());
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
    void rejectsCustomerForeignCurrencyAndReusedIdempotencyKeyBeforeNumbering() {
        CurrentAccountResponse customer = currentAccountService.create(
            company.id(),
            actorId,
            currentAccount("Customer", "6200000000", RelationshipType.CUSTOMER)
        );
        UUID customerKey = UUID.randomUUID();

        assertThatThrownBy(() -> paymentVoucherService.create(
            company.id(),
            actorId,
            request(customerKey, customer.id(), SettlementAccountType.CASH, cashAccount.id(), "100")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PAYMENT_CURRENT_ACCOUNT_INVALID");

        CashAccountResponse dollarCash = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Dollar Cash", "USD")
        );
        UUID currencyKey = UUID.randomUUID();
        assertThatThrownBy(() -> paymentVoucherService.create(
            company.id(),
            actorId,
            request(currencyKey, supplier.id(), SettlementAccountType.CASH, dollarCash.id(), "100")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PAYMENT_ACCOUNT_CURRENCY_MISMATCH");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM voucher_number_sequences WHERE company_id = ? AND voucher_type = 'PAYMENT'",
            Integer.class,
            company.id()
        )).isZero();

        UUID reusedKey = UUID.randomUUID();
        paymentVoucherService.create(
            company.id(),
            actorId,
            request(reusedKey, supplier.id(), SettlementAccountType.CASH, cashAccount.id(), "100")
        );
        assertThatThrownBy(() -> paymentVoucherService.create(
            company.id(),
            actorId,
            request(reusedKey, supplier.id(), SettlementAccountType.CASH, cashAccount.id(), "101")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        assertThat(voucherCount(reusedKey)).isEqualTo(1);
        assertThat(sequenceValue()).isEqualTo(1);
    }

    private VoucherResponse createConcurrent(
        CountDownLatch ready,
        CountDownLatch start,
        CreatePaymentVoucherRequest request
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out.");
        }
        return paymentVoucherService.create(company.id(), actorId, request);
    }

    private VoucherResponse result(Future<VoucherResponse> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private String approveConcurrent(
        CountDownLatch ready,
        CountDownLatch start,
        UUID voucherId
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent approval start timed out.");
        }
        try {
            voucherService.approve(company.id(), actorId, voucherId);
            return "APPROVED";
        } catch (BusinessException exception) {
            return exception.code();
        }
    }

    private String approvalResult(Future<String> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private CreatePaymentVoucherRequest request(
        UUID idempotencyKey,
        UUID currentAccountId,
        SettlementAccountType type,
        UUID settlementAccountId,
        String amount
    ) {
        return new CreatePaymentVoucherRequest(
            idempotencyKey,
            currentAccountId,
            type,
            settlementAccountId,
            new BigDecimal(amount),
            LocalDate.of(2026, 7, 16),
            "Supplier payment",
            "PAY-1"
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
              AND voucher_type = 'PAYMENT'
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
            VALUES (?, ?, ?, ?, ?, 'Payment', 'User', 'ACTIVE', false)
            """,
            id,
            companyId,
            "payment-user-" + id,
            id + "@example.com",
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ12345"
        );
        return id;
    }

    private Company company(String source) {
        String digits = source.replaceAll("\\D", "");
        String taxNumber = (digits + "0000000000").substring(0, 10);
        return Company.create(
            "Payment Test Company " + taxNumber,
            "Payment Test Company " + taxNumber,
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
