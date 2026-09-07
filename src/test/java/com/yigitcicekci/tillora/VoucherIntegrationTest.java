package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.chartofaccount.application.service.LedgerAccountProvisioningService;
import com.yigitcicekci.tillora.chartofaccount.application.service.PostingAccountProvision;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import com.yigitcicekci.tillora.voucher.api.request.CreateVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.UpdateVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.VoucherLineRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.application.service.VoucherService;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest
class VoucherIntegrationTest {

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
    private LedgerAccountProvisioningService ledgerAccountProvisioningService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Company company;
    private UUID actorId;
    private UUID debitAccountId;
    private UUID creditAccountId;

    @BeforeEach
    void createCompanyAndPostingAccounts() {
        company = companyRepository.saveAndFlush(company("40" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(company.id());
        userAccessSetupService.initializeForCompany(company.id());
        actorId = insertUser(company.id(), "voucher-user-" + UUID.randomUUID(), UUID.randomUUID() + "@example.com");
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        PostingAccountProvision debitAccount = transactionTemplate.execute(
            status -> ledgerAccountProvisioningService.createCashPostingAccount(company.id(), "Voucher Debit")
        );
        PostingAccountProvision creditAccount = transactionTemplate.execute(
            status -> ledgerAccountProvisioningService.createCashPostingAccount(company.id(), "Voucher Credit")
        );
        debitAccountId = debitAccount.chartOfAccountId();
        creditAccountId = creditAccount.chartOfAccountId();
    }

    @Test
    void createsUpdatesApprovesAndCancelsOffsetVoucherWithoutDuplicatingTransitions() {
        VoucherResponse created = voucherService.create(
            company.id(),
            actorId,
            request(
                LocalDate.of(2026, 7, 16),
                "  Opening adjustment  ",
                "  DOC-1  ",
                line(debitAccountId, "  debit line  ", "100", "0"),
                line(creditAccountId, "credit line", "0", "90")
            )
        );

        assertThat(created.voucherNumber()).isEqualTo("MHS-2026-000001");
        assertThat(created.status()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(created.movementNote()).isEqualTo("Opening adjustment");
        assertThat(created.documentNumber()).isEqualTo("DOC-1");
        assertThat(created.currency()).isEqualTo("TRY");
        assertThat(created.exchangeRate()).isEqualByComparingTo("1");
        assertThat(created.totalDebit()).isEqualByComparingTo("100");
        assertThat(created.totalCredit()).isEqualByComparingTo("90");
        assertThat(created.lines()).extracting(line -> line.lineNumber()).containsExactly(1, 2);
        assertThat(created.lines()).extracting(line -> line.accountCode()).containsExactly("100.01", "100.02");
        assertThat(created.lines()).extracting(line -> line.accountName())
            .containsExactly("Voucher Debit", "Voucher Credit");

        List<UUID> originalLineIds = created.lines().stream().map(line -> line.id()).toList();
        assertThatThrownBy(() -> voucherService.update(
            company.id(),
            actorId,
            created.id(),
            new UpdateVoucherRequest(
                LocalDate.of(2026, 8, 1),
                "Invalid replacement",
                null,
                null,
                List.of(
                    line(debitAccountId, null, "50", "0"),
                    line(UUID.randomUUID(), null, "0", "50")
                )
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_POSTING_ACCOUNT_INVALID");
        assertThat(voucherService.get(company.id(), created.id()).lines())
            .extracting(line -> line.id())
            .containsExactlyElementsOf(originalLineIds);

        assertThatThrownBy(() -> voucherService.update(
            company.id(),
            actorId,
            created.id(),
            new UpdateVoucherRequest(
                LocalDate.of(2027, 1, 1),
                null,
                null,
                null,
                List.of(
                    line(debitAccountId, null, "125", "0"),
                    line(creditAccountId, null, "0", "125")
                )
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_YEAR_CANNOT_CHANGE");

        VoucherResponse updated = voucherService.update(
            company.id(),
            actorId,
            created.id(),
            new UpdateVoucherRequest(
                LocalDate.of(2026, 12, 31),
                "Year end",
                "DOC-2",
                "try",
                List.of(
                    line(debitAccountId, "new debit", "125", "0"),
                    line(creditAccountId, "new credit", "0", "125")
                )
            )
        );
        assertThat(updated.lines()).extracting(line -> line.id()).doesNotContainAnyElementsOf(originalLineIds);
        assertThat(updated.totalDebit()).isEqualByComparingTo("125");
        assertThat(updated.totalCredit()).isEqualByComparingTo("125");
        assertThat(updated.voucherNumber()).isEqualTo(created.voucherNumber());

        VoucherResponse approved = voucherService.approve(company.id(), actorId, created.id());
        VoucherResponse approvedAgain = voucherService.approve(company.id(), actorId, created.id());
        assertThat(approved.status()).isEqualTo(VoucherStatus.APPROVED);
        assertThat(approvedAgain.approvedAt()).isCloseTo(approved.approvedAt(), within(1, ChronoUnit.MICROS));
        assertThat(auditCount(created.id(), "VOUCHER_APPROVE")).isEqualTo(1);
        assertThatThrownBy(() -> voucherService.update(
            company.id(),
            actorId,
            created.id(),
            new UpdateVoucherRequest(
                LocalDate.of(2026, 12, 31),
                null,
                null,
                null,
                List.of(
                    line(debitAccountId, null, "125", "0"),
                    line(creditAccountId, null, "0", "125")
                )
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_NOT_DRAFT");

        List<UUID> approvedLineIds = approved.lines().stream().map(line -> line.id()).toList();
        VoucherResponse cancelled = voucherService.cancel(company.id(), actorId, created.id(), "  Entered twice  ");
        VoucherResponse cancelledAgain = voucherService.cancel(company.id(), actorId, created.id(), "Entered twice");
        assertThat(cancelled.status()).isEqualTo(VoucherStatus.CANCELLED);
        assertThat(cancelled.cancellationReason()).isEqualTo("Entered twice");
        assertThat(cancelled.lines()).extracting(line -> line.id()).containsExactlyElementsOf(approvedLineIds);
        assertThat(cancelledAgain.cancelledAt()).isCloseTo(cancelled.cancelledAt(), within(1, ChronoUnit.MICROS));
        assertThat(cancelled.approvedAt()).isCloseTo(approved.approvedAt(), within(1, ChronoUnit.MICROS));
        assertThat(cancelled.approvedBy()).isEqualTo(actorId);
        assertThat(auditCount(created.id(), "VOUCHER_CANCEL")).isEqualTo(1);
        assertThat(auditCount(created.id(), "VOUCHER_CREATE")).isEqualTo(1);
        assertThat(auditCount(created.id(), "VOUCHER_UPDATE")).isEqualTo(1);
        assertThat(voucherService.list(
            company.id(),
            VoucherType.OFFSET,
            VoucherStatus.CANCELLED,
            LocalDate.of(2026, 1, 1),
            LocalDate.of(2026, 12, 31),
            PageRequest.of(0, 20)
        ).getContent()).extracting(summary -> summary.id()).containsExactly(created.id());
    }

    @Test
    void rejectsUnbalancedApprovalAndAccountDeactivationWithoutStateOrAuditChanges() {
        VoucherResponse unbalanced = voucherService.create(
            company.id(),
            actorId,
            request(
                LocalDate.of(2026, 7, 16),
                null,
                null,
                line(debitAccountId, null, "100", "0"),
                line(creditAccountId, null, "0", "99")
            )
        );

        assertThatThrownBy(() -> voucherService.approve(company.id(), actorId, unbalanced.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_NOT_BALANCED");
        assertThat(voucherService.get(company.id(), unbalanced.id()).status()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(auditCount(unbalanced.id(), "VOUCHER_APPROVE")).isZero();

        VoucherResponse balanced = voucherService.create(
            company.id(),
            actorId,
            request(
                LocalDate.of(2026, 7, 16),
                null,
                null,
                line(debitAccountId, null, "75", "0"),
                line(creditAccountId, null, "0", "75")
            )
        );
        new TransactionTemplate(transactionManager).executeWithoutResult(
            status -> ledgerAccountProvisioningService.disableCashPostingAccount(company.id(), debitAccountId)
        );

        assertThatThrownBy(() -> voucherService.approve(company.id(), actorId, balanced.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_POSTING_ACCOUNT_INVALID");
        assertThat(voucherService.get(company.id(), balanced.id()).status()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(auditCount(balanced.id(), "VOUCHER_APPROVE")).isZero();
    }

    @Test
    void concurrentCreatesReceiveContiguousDatabaseBackedNumbers() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<VoucherResponse>> futures = List.of(
                executor.submit(() -> createConcurrentVoucher(ready, start, "First")),
                executor.submit(() -> createConcurrentVoucher(ready, start, "Second"))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            assertThat(futures.stream().map(this::result).map(VoucherResponse::voucherNumber).toList())
                .containsExactlyInAnyOrder("MHS-2026-000001", "MHS-2026-000002");
        }

        Integer sequence = jdbcTemplate.queryForObject(
            """
            SELECT current_value
            FROM voucher_number_sequences
            WHERE company_id = ?
              AND voucher_type = 'OFFSET'
              AND voucher_year = 2026
            """,
            Integer.class,
            company.id()
        );
        assertThat(sequence).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vouchers WHERE company_id = ?",
            Integer.class,
            company.id()
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE company_id = ? AND action = 'VOUCHER_CREATE'",
            Integer.class,
            company.id()
        )).isEqualTo(2);
    }

    @Test
    void concurrentRetriesCreateOneOffsetVoucherAndRejectChangedPayload() throws Exception {
        UUID idempotencyKey = UUID.randomUUID();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<VoucherResponse> responses;
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<VoucherResponse>> futures = List.of(
                executor.submit(() -> createConcurrentVoucher(ready, start, idempotencyKey, "Retry")),
                executor.submit(() -> createConcurrentVoucher(ready, start, idempotencyKey, "Retry"))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            responses = futures.stream().map(this::result).toList();
        }

        assertThat(responses).extracting(VoucherResponse::id).containsOnly(responses.getFirst().id());
        assertThat(responses).extracting(VoucherResponse::voucherNumber).containsOnly("MHS-2026-000001");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vouchers WHERE company_id = ? AND idempotency_key = ?",
            Integer.class,
            company.id(),
            idempotencyKey
        )).isEqualTo(1);
        assertThat(auditCount(responses.getFirst().id(), "VOUCHER_CREATE")).isEqualTo(1);
        assertThatThrownBy(() -> voucherService.create(
            company.id(),
            actorId,
            request(
                idempotencyKey,
                LocalDate.of(2026, 7, 16),
                "Changed",
                null,
                line(debitAccountId, null, "10", "0"),
                line(creditAccountId, null, "0", "10")
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void enforcesTenantPermissionsAndDatabaseLineConstraints() {
        Company otherCompany = companyRepository.saveAndFlush(company("41" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(otherCompany.id());
        userAccessSetupService.initializeForCompany(otherCompany.id());
        UUID otherActor = insertUser(
            otherCompany.id(),
            "other-voucher-user-" + UUID.randomUUID(),
            UUID.randomUUID() + "@example.com"
        );

        assertThatThrownBy(() -> voucherService.create(
            otherCompany.id(),
            otherActor,
            request(
                LocalDate.of(2026, 7, 16),
                null,
                null,
                line(debitAccountId, null, "10", "0"),
                line(creditAccountId, null, "0", "10")
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_POSTING_ACCOUNT_INVALID");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM voucher_number_sequences WHERE company_id = ?",
            Integer.class,
            otherCompany.id()
        )).isZero();

        VoucherResponse created = voucherService.create(
            company.id(),
            actorId,
            request(
                LocalDate.of(2026, 7, 16),
                null,
                null,
                line(debitAccountId, null, "10", "0"),
                line(creditAccountId, null, "0", "10")
            )
        );
        Map<String, Object> account = jdbcTemplate.queryForMap(
            "SELECT code, name FROM chart_of_accounts WHERE id = ?",
            debitAccountId
        );
        assertThatThrownBy(() -> jdbcTemplate.update(
            """
            INSERT INTO voucher_lines (
                id,
                company_id,
                voucher_id,
                line_number,
                chart_of_account_id,
                chart_account_code,
                chart_account_name,
                debit,
                credit
            )
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(),
            company.id(),
            created.id(),
            3,
            debitAccountId,
            account.get("code"),
            account.get("name"),
            new BigDecimal("10.0000"),
            new BigDecimal("10.0000")
        )).isInstanceOf(DataIntegrityViolationException.class);

        assertThat(permissionCodes("ADMIN"))
            .contains("VOUCHER_READ", "VOUCHER_CREATE", "VOUCHER_APPROVE", "VOUCHER_CANCEL");
        assertThat(permissionCodes("ACCOUNTING"))
            .contains("VOUCHER_READ", "VOUCHER_CREATE", "VOUCHER_APPROVE", "VOUCHER_CANCEL");
        assertThat(permissionCodes("VIEWER"))
            .contains("VOUCHER_READ")
            .doesNotContain("VOUCHER_CREATE", "VOUCHER_APPROVE", "VOUCHER_CANCEL");
    }

    private VoucherResponse createConcurrentVoucher(
        CountDownLatch ready,
        CountDownLatch start,
        String note
    ) throws InterruptedException {
        return createConcurrentVoucher(ready, start, UUID.randomUUID(), note);
    }

    private VoucherResponse createConcurrentVoucher(
        CountDownLatch ready,
        CountDownLatch start,
        UUID idempotencyKey,
        String note
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out.");
        }
        return voucherService.create(
            company.id(),
            actorId,
            request(
                idempotencyKey,
                LocalDate.of(2026, 7, 16),
                note,
                null,
                line(debitAccountId, null, "10", "0"),
                line(creditAccountId, null, "0", "10")
            )
        );
    }

    private VoucherResponse result(Future<VoucherResponse> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private CreateVoucherRequest request(
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        VoucherLineRequest... lines
    ) {
        return request(UUID.randomUUID(), voucherDate, movementNote, documentNumber, lines);
    }

    private CreateVoucherRequest request(
        UUID idempotencyKey,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        VoucherLineRequest... lines
    ) {
        return new CreateVoucherRequest(
            idempotencyKey,
            VoucherType.OFFSET,
            voucherDate,
            movementNote,
            documentNumber,
            null,
            List.of(lines)
        );
    }

    private VoucherLineRequest line(
        UUID chartOfAccountId,
        String movementNote,
        String debit,
        String credit
    ) {
        return new VoucherLineRequest(
            chartOfAccountId,
            movementNote,
            new BigDecimal(debit),
            new BigDecimal(credit),
            null,
            null
        );
    }

    private int auditCount(UUID voucherId, String action) {
        return jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM audit_logs
            WHERE company_id = ?
              AND entity_id = ?
              AND action = ?
            """,
            Integer.class,
            company.id(),
            voucherId,
            action
        );
    }

    private List<String> permissionCodes(String roleName) {
        return jdbcTemplate.queryForList(
            """
            SELECT permission.code
            FROM roles role
            JOIN role_permissions mapping ON mapping.role_id = role.id
            JOIN permissions permission ON permission.id = mapping.permission_id
            WHERE role.company_id = ?
              AND role.name = ?
            ORDER BY permission.code
            """,
            String.class,
            company.id(),
            roleName
        );
    }

    private UUID insertUser(UUID companyId, String username, String email) {
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
            VALUES (?, ?, ?, ?, ?, 'Voucher', 'User', 'ACTIVE', false)
            """,
            id,
            companyId,
            username,
            email,
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ12345"
        );
        return id;
    }

    private Company company(String source) {
        String digits = source.replaceAll("\\D", "");
        String taxNumber = (digits + "0000000000").substring(0, 10);
        return Company.create(
            "Voucher Test Company " + taxNumber,
            "Voucher Test Company " + taxNumber,
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
