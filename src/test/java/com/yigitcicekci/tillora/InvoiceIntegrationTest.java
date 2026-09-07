package com.yigitcicekci.tillora;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.audit.application.service.AuditQueryService;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.currentaccount.api.request.CreateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.response.CurrentAccountResponse;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardSummary;
import com.yigitcicekci.tillora.dashboard.application.service.DashboardService;
import com.yigitcicekci.tillora.finance.api.request.CreateCashAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.CashAccountResponse;
import com.yigitcicekci.tillora.finance.application.service.CashAccountService;
import com.yigitcicekci.tillora.invoice.api.request.CreatePurchaseInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.request.CreateSalesInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.request.CreateInvoiceSettlementRequest;
import com.yigitcicekci.tillora.invoice.api.request.InvoiceLineRequest;
import com.yigitcicekci.tillora.invoice.api.request.UpdateInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.response.InvoiceResponse;
import com.yigitcicekci.tillora.invoice.application.service.InvoiceService;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoicePaymentStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceSettlementAccountType;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import com.yigitcicekci.tillora.product.api.request.CreateProductRequest;
import com.yigitcicekci.tillora.product.api.response.ProductResponse;
import com.yigitcicekci.tillora.product.application.service.ProductService;
import com.yigitcicekci.tillora.product.domain.enumeration.ProductUnit;
import com.yigitcicekci.tillora.reporting.application.service.ReportingService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import com.yigitcicekci.tillora.voucher.application.service.VoucherService;
import com.yigitcicekci.tillora.voucher.application.service.CollectionVoucherService;
import com.yigitcicekci.tillora.voucher.api.request.CreateCollectionVoucherRequest;
import com.yigitcicekci.tillora.voucher.domain.enumeration.SettlementAccountType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@ActiveProfiles("test")
@SpringBootTest
class InvoiceIntegrationTest {

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
    private ProductService productService;

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private ReportingService reportingService;

    @Autowired
    private AuditQueryService auditQueryService;

    @Autowired
    private CashAccountService cashAccountService;

    @Autowired
    private CollectionVoucherService collectionVoucherService;

    @Autowired
    private VoucherService voucherService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Company company;
    private UUID actorId;
    private CurrentAccountResponse customer;
    private CurrentAccountResponse supplier;
    private ProductResponse product;

    @BeforeEach
    void createInvoiceContext() {
        company = companyRepository.saveAndFlush(company("92" + System.nanoTime()));
        defaultAccountingSetupService.initializeForCompany(company.id());
        userAccessSetupService.initializeForCompany(company.id());
        actorId = insertUser(company.id());
        customer = currentAccountService.create(
            company.id(),
            actorId,
            new CreateCurrentAccountRequest(
                "Invoice Customer",
                null,
                "9200000001",
                null,
                null,
                null,
                null,
                null,
                TradeType.RETAIL,
                RelationshipType.CUSTOMER
            )
        );
        supplier = currentAccountService.create(
            company.id(),
            actorId,
            new CreateCurrentAccountRequest(
                "Invoice Supplier",
                null,
                "9200000002",
                null,
                null,
                null,
                null,
                null,
                TradeType.RETAIL,
                RelationshipType.SUPPLIER
            )
        );
        product = productService.create(
            company.id(),
            actorId,
            new CreateProductRequest(
                "INV-PRD-001",
                null,
                "Invoice Product",
                ProductUnit.PIECE,
                new BigDecimal("40"),
                new BigDecimal("100"),
                new BigDecimal("20")
            )
        );
    }

    @Test
    void createsUpdatesListsAndReturnsIdempotentSalesDraft() {
        UUID idempotencyKey = UUID.randomUUID();
        CreateSalesInvoiceRequest request = request(idempotencyKey, "2", "100", "10", "20");

        InvoiceResponse created = invoiceService.createSales(company.id(), actorId, request);
        InvoiceResponse repeated = invoiceService.createSales(company.id(), actorId, request);

        assertThat(repeated.id()).isEqualTo(created.id());
        assertThat(created.invoiceNumber()).isEqualTo("SF-2026-000001");
        assertThat(created.invoiceType()).isEqualTo(InvoiceType.SALES);
        assertThat(created.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(created.subtotal()).isEqualByComparingTo("200.0000");
        assertThat(created.discountTotal()).isEqualByComparingTo("20.0000");
        assertThat(created.taxTotal()).isEqualByComparingTo("36.0000");
        assertThat(created.grandTotal()).isEqualByComparingTo("216.0000");
        assertThat(created.costTotal()).isEqualByComparingTo("80.0000");
        assertThat(created.lines()).singleElement()
            .satisfies(line -> {
                assertThat(line.description()).isEqualTo("Invoice Product");
                assertThat(line.unit()).isEqualTo("PIECE");
            });

        InvoiceResponse updated = invoiceService.update(
            company.id(),
            actorId,
            created.id(),
            new UpdateInvoiceRequest(
                customer.id(),
                LocalDate.of(2026, 7, 17),
                LocalDate.of(2026, 8, 17),
                "TRY",
                List.of(line("1", "150", "0", "20"))
            )
        );

        assertThat(updated.grandTotal()).isEqualByComparingTo("180.0000");
        assertThat(updated.costTotal()).isEqualByComparingTo("40.0000");
        assertThat(invoiceService.list(
            company.id(),
            InvoiceType.SALES,
            InvoiceStatus.DRAFT,
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            PageRequest.of(0, 20)
        ).getContent()).extracting(summary -> summary.id()).containsExactly(created.id());
        assertThat(count("invoices")).isEqualTo(1);
        assertThat(count("invoice_lines")).isEqualTo(1);
        assertThat(auditCount(created.id(), "INVOICE_CREATE")).isEqualTo(1);
        assertThat(auditCount(created.id(), "INVOICE_UPDATE")).isEqualTo(1);
    }

    @Test
    void rejectsInvoiceVatRateDifferentFromProductConfiguration() {
        assertThatThrownBy(() -> invoiceService.createSales(
            company.id(),
            actorId,
            request(UUID.randomUUID(), "1", "100", "0", "0")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVOICE_VAT_RATE_MISMATCH");
        assertThat(count("invoices")).isZero();
        assertThat(count("invoice_lines")).isZero();
    }

    @Test
    void approvesSalesInvoiceWithAtomicFinancialPostings() {
        InvoiceResponse draft = invoiceService.createSales(
            company.id(),
            actorId,
            request(UUID.randomUUID(), "2", "100", "10", "20")
        );

        InvoiceResponse approved = invoiceService.approve(company.id(), actorId, draft.id());
        InvoiceResponse repeated = invoiceService.approve(company.id(), actorId, draft.id());

        assertThat(approved.status()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(repeated.accountingVoucherId()).isEqualTo(approved.accountingVoucherId());
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT debit
            FROM current_account_movements
            WHERE company_id = ?
              AND reference_id = ?
            """,
            BigDecimal.class,
            company.id(),
            draft.id()
        )).isEqualByComparingTo("216.0000");
        assertThat(jdbcTemplate.queryForList(
            """
            SELECT chart_account_code
            FROM voucher_lines
            WHERE company_id = ?
              AND voucher_id = ?
            ORDER BY line_number
            """,
            String.class,
            company.id(),
            approved.accountingVoucherId()
        )).containsExactly(
            customer.ledgerAccounts().getFirst().fullAccountCode(),
            "600",
            "391",
            "621",
            "153"
        );
        assertThat(jdbcTemplate.queryForObject(
            "SELECT total_debit FROM vouchers WHERE id = ?",
            BigDecimal.class,
            approved.accountingVoucherId()
        )).isEqualByComparingTo("296.0000");
        assertThat(auditCount(draft.id(), "INVOICE_APPROVE")).isEqualTo(1);
        assertThat(count("current_account_movements")).isEqualTo(1);
        assertThat(sourceVoucherCount(draft.id())).isEqualTo(1);

        assertThatThrownBy(() -> voucherService.cancel(
            company.id(),
            actorId,
            approved.accountingVoucherId(),
            "Direct cancellation"
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("SOURCE_VOUCHER_CANNOT_BE_CANCELLED_DIRECTLY");
    }

    @Test
    void approvesCashSaleAtomicallyAndTracksSettlementIdempotently() {
        CashAccountResponse cash = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Invoice Cash", "TRY")
        );
        InvoiceResponse draft = invoiceService.createSales(
            company.id(),
            actorId,
            request(UUID.randomUUID(), "1", "100", "0", "20")
        );
        CreateInvoiceSettlementRequest settlement = settlement(
            UUID.randomUUID(),
            cash.id(),
            "120.0000"
        );

        InvoiceResponse approved = invoiceService.approveWithSettlement(
            company.id(),
            actorId,
            draft.id(),
            settlement
        );
        InvoiceResponse repeated = invoiceService.approveWithSettlement(
            company.id(),
            actorId,
            draft.id(),
            settlement
        );

        assertThat(approved.status()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(approved.paymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(approved.paidAmount()).isEqualByComparingTo("120.0000");
        assertThat(approved.remainingAmount()).isEqualByComparingTo("0.0000");
        assertThat(repeated.paymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(count("invoice_settlement_allocations")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT count(*) FROM vouchers WHERE company_id = ?",
            Integer.class,
            company.id()
        )).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT sum(line.debit - line.credit)
            FROM voucher_lines line
            JOIN cash_accounts cash ON cash.company_id = line.company_id
                                   AND cash.chart_of_account_id = line.chart_of_account_id
            WHERE line.company_id = ?
              AND cash.id = ?
            """,
            BigDecimal.class,
            company.id(),
            cash.id()
        )).isEqualByComparingTo("120.0000");
        assertThatThrownBy(() -> invoiceService.approveWithSettlement(
            company.id(),
            actorId,
            draft.id(),
            settlement(settlement.idempotencyKey(), cash.id(), "119.0000")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("IDEMPOTENCY_KEY_REUSED");
    }

    @Test
    void supportsPartialInvoiceSettlementsAndExcludesCancelledVoucher() {
        CashAccountResponse cash = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Partial Cash", "TRY")
        );
        InvoiceResponse draft = invoiceService.createSales(
            company.id(),
            actorId,
            request(UUID.randomUUID(), "1", "100", "0", "20")
        );

        InvoiceResponse partial = invoiceService.approveWithSettlement(
            company.id(),
            actorId,
            draft.id(),
            settlement(UUID.randomUUID(), cash.id(), "50.0000")
        );
        InvoiceResponse paid = invoiceService.settle(
            company.id(),
            actorId,
            draft.id(),
            settlement(UUID.randomUUID(), cash.id(), "70.0000")
        );

        assertThat(partial.paymentStatus()).isEqualTo(InvoicePaymentStatus.PARTIALLY_PAID);
        assertThat(partial.remainingAmount()).isEqualByComparingTo("70.0000");
        assertThat(paid.paymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        UUID lastVoucherId = jdbcTemplate.queryForObject(
            """
            SELECT voucher_id
            FROM invoice_settlement_allocations
            WHERE company_id = ? AND invoice_id = ? AND amount = 70.0000
            """,
            UUID.class,
            company.id(),
            draft.id()
        );
        voucherService.cancel(company.id(), actorId, lastVoucherId, "Incorrect collection");

        InvoiceResponse afterCancellation = invoiceService.get(company.id(), draft.id());
        assertThat(afterCancellation.paymentStatus()).isEqualTo(InvoicePaymentStatus.PARTIALLY_PAID);
        assertThat(afterCancellation.paidAmount()).isEqualByComparingTo("50.0000");
        assertThat(afterCancellation.remainingAmount()).isEqualByComparingTo("70.0000");
    }

    @Test
    void concurrentSettlementsCannotExceedInvoiceRemainingAmount() throws Exception {
        CashAccountResponse cash = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Concurrent Cash", "TRY")
        );
        InvoiceResponse draft = invoiceService.createSales(
            company.id(),
            actorId,
            request(UUID.randomUUID(), "1", "100", "0", "20")
        );
        invoiceService.approveWithSettlement(
            company.id(),
            actorId,
            draft.id(),
            settlement(UUID.randomUUID(), cash.id(), "50.0000")
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                executor.submit(() -> settleOutcome(
                    ready,
                    start,
                    draft.id(),
                    settlement(UUID.randomUUID(), cash.id(), "70.0000")
                )),
                executor.submit(() -> settleOutcome(
                    ready,
                    start,
                    draft.id(),
                    settlement(UUID.randomUUID(), cash.id(), "70.0000")
                ))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = futures.stream().map(this::result).toList();
        }

        assertThat(outcomes.stream().filter(InvoiceResponse.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(BusinessException.class::isInstance)).singleElement()
            .satisfies(exception -> assertThat(((BusinessException) exception).code())
                .isEqualTo("INVOICE_SETTLEMENT_EXCEEDS_REMAINING_AMOUNT"));
        assertThat(invoiceService.get(company.id(), draft.id()).paymentStatus())
            .isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(count("invoice_settlement_allocations")).isEqualTo(2);
    }

    @Test
    void rejectsExcessSettlementAndRollsBackInvoiceApproval() {
        CashAccountResponse cash = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Rollback Cash", "TRY")
        );
        InvoiceResponse draft = invoiceService.createSales(
            company.id(),
            actorId,
            request(UUID.randomUUID(), "1", "100", "0", "20")
        );

        assertThatThrownBy(() -> invoiceService.approveWithSettlement(
            company.id(),
            actorId,
            draft.id(),
            settlement(UUID.randomUUID(), cash.id(), "120.0001")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVOICE_SETTLEMENT_EXCEEDS_REMAINING_AMOUNT");

        assertThatThrownBy(() -> invoiceService.approveWithSettlement(
            company.id(),
            actorId,
            draft.id(),
            settlement(UUID.randomUUID(), UUID.randomUUID(), "120.0000")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("CASH_ACCOUNT_NOT_AVAILABLE_FOR_POSTING");

        assertThat(invoiceService.get(company.id(), draft.id()).status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(sourceVoucherCount(draft.id())).isZero();
        assertThat(count("invoice_settlement_allocations")).isZero();
    }

    @Test
    void approvesCashPurchaseWithPaymentVoucher() {
        CashAccountResponse cash = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Purchase Cash", "TRY")
        );
        var openingCollection = collectionVoucherService.create(
            company.id(),
            actorId,
            new CreateCollectionVoucherRequest(
                UUID.randomUUID(),
                customer.id(),
                SettlementAccountType.CASH,
                cash.id(),
                new BigDecimal("120.0000"),
                LocalDate.of(2026, 7, 17),
                "Opening cash balance",
                null
            )
        );
        voucherService.approve(company.id(), actorId, openingCollection.id());
        InvoiceResponse draft = invoiceService.createPurchase(
            company.id(),
            actorId,
            purchaseRequest(UUID.randomUUID(), "1", "100", "0", "20")
        );

        InvoiceResponse approved = invoiceService.approveWithSettlement(
            company.id(),
            actorId,
            draft.id(),
            settlement(UUID.randomUUID(), cash.id(), "120.0000")
        );

        assertThat(approved.paymentStatus()).isEqualTo(InvoicePaymentStatus.PAID);
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT sum(line.debit - line.credit)
            FROM voucher_lines line
            JOIN vouchers voucher ON voucher.id = line.voucher_id
                                 AND voucher.company_id = line.company_id
            JOIN cash_accounts cash ON cash.company_id = line.company_id
                                   AND cash.chart_of_account_id = line.chart_of_account_id
            WHERE line.company_id = ?
              AND cash.id = ?
              AND voucher.voucher_type = 'PAYMENT'
            """,
            BigDecimal.class,
            company.id(),
            cash.id()
        )).isEqualByComparingTo("-120.0000");
    }

    @Test
    void summarizesDashboardFromTenantScopedFinancialData() {
        InvoiceResponse sales = invoiceService.createSales(
            company.id(),
            actorId,
            request(
                UUID.randomUUID(),
                "2",
                "100",
                "10",
                "20",
                dashboardDueDate()
            )
        );
        invoiceService.approve(company.id(), actorId, sales.id());
        InvoiceResponse purchase = invoiceService.createPurchase(
            company.id(),
            actorId,
            purchaseRequest(UUID.randomUUID(), "1", "100", "0", "20")
        );
        invoiceService.approve(company.id(), actorId, purchase.id());
        CashAccountResponse cash = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Dashboard Cash", "TRY")
        );
        var collection = collectionVoucherService.create(
            company.id(),
            actorId,
            new CreateCollectionVoucherRequest(
                UUID.randomUUID(),
                customer.id(),
                SettlementAccountType.CASH,
                cash.id(),
                new BigDecimal("50.0000"),
                LocalDate.of(2026, 7, 20),
                "Dashboard collection",
                null
            )
        );
        voucherService.approve(company.id(), actorId, collection.id());

        DashboardSummary summary = dashboardService.summary(
            company.id(),
            YearMonth.of(2026, 7)
        );

        assertThat(summary.currency()).isEqualTo("TRY");
        assertThat(summary.cards().totalReceivables()).isEqualByComparingTo("166.0000");
        assertThat(summary.cards().overdueReceivables()).isEqualByComparingTo("0.0000");
        assertThat(summary.cards().totalPayables()).isEqualByComparingTo("120.0000");
        assertThat(summary.cards().cashBalance()).isEqualByComparingTo("50.0000");
        assertThat(summary.cards().bankBalance()).isEqualByComparingTo("0.0000");
        assertThat(summary.cards().monthlySales()).isEqualByComparingTo("216.0000");
        assertThat(summary.cards().monthlyPurchases()).isEqualByComparingTo("120.0000");
        assertThat(summary.cards().collections()).isEqualByComparingTo("50.0000");
        assertThat(summary.cards().payments()).isEqualByComparingTo("0.0000");

        Company anotherCompany = companyRepository.saveAndFlush(company("93" + System.nanoTime()));
        DashboardSummary isolated = dashboardService.summary(
            anotherCompany.id(),
            YearMonth.of(2026, 7)
        );
        assertThat(isolated.cards().monthlySales()).isEqualByComparingTo("0.0000");
        assertThat(isolated.cards().totalReceivables()).isEqualByComparingTo("0.0000");
    }

    @Test
    void returnsTenantScopedFilteredAndPaginatedReports() {
        InvoiceResponse sales = invoiceService.createSales(
            company.id(),
            actorId,
            request(UUID.randomUUID(), "2", "100", "10", "20")
        );
        invoiceService.approve(company.id(), actorId, sales.id());
        InvoiceResponse purchase = invoiceService.createPurchase(
            company.id(),
            actorId,
            purchaseRequest(UUID.randomUUID(), "1", "100", "0", "20")
        );
        invoiceService.approve(company.id(), actorId, purchase.id());
        CashAccountResponse cash = cashAccountService.create(
            company.id(),
            actorId,
            new CreateCashAccountRequest("Reporting Cash", "TRY")
        );
        var collection = collectionVoucherService.create(
            company.id(),
            actorId,
            new CreateCollectionVoucherRequest(
                UUID.randomUUID(),
                customer.id(),
                SettlementAccountType.CASH,
                cash.id(),
                new BigDecimal("50.0000"),
                LocalDate.of(2026, 7, 20),
                "Reporting collection",
                null
            )
        );
        voucherService.approve(company.id(), actorId, collection.id());
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        PageRequest page = PageRequest.of(0, 20);

        var customerStatement = reportingService.currentAccountStatement(
            company.id(), customer.id(), from, to, page
        );
        assertThat(customerStatement).hasSize(2);
        assertThat(customerStatement.getContent())
            .anySatisfy(row -> assertThat(row.movementNote()).isEqualTo("Sales invoice " + sales.invoiceNumber()));
        assertThat(reportingService.currentAccountStatement(
            company.id(), supplier.id(), from, to, page
        ).getContent())
            .singleElement()
            .satisfies(row -> assertThat(row.movementNote()).isEqualTo("Purchase invoice " + purchase.invoiceNumber()));
        assertThat(reportingService.receivables(company.id(), from, to, page).getContent())
            .singleElement()
            .satisfies(row -> assertThat(row.balance()).isEqualByComparingTo("166.0000"));
        assertThat(reportingService.payables(company.id(), from, to, page).getContent())
            .singleElement()
            .satisfies(row -> assertThat(row.balance()).isEqualByComparingTo("120.0000"));
        var voucherReport = reportingService.vouchers(
            company.id(),
            from,
            to,
            null,
            "APPROVED",
            PageRequest.of(0, 1, Sort.by(Sort.Direction.DESC, "voucherDate"))
        );
        assertThat(voucherReport.getContent()).hasSize(1);
        assertThat(voucherReport.getTotalElements()).isEqualTo(3);
        assertThat(reportingService.cashMovements(company.id(), cash.id(), from, to, page))
            .hasSize(1);
        assertThat(reportingService.bankMovements(company.id(), null, from, to, page)).isEmpty();
        assertThat(reportingService.sales(company.id(), from, to, "APPROVED", page)).hasSize(1);
        assertThat(reportingService.purchases(company.id(), from, to, "APPROVED", page)).hasSize(1);
        assertThat(reportingService.audit(
            company.id(), from, LocalDate.now(), null, null, page
        )).isNotEmpty();
        assertThat(reportingService.sales(
            company.id(), LocalDate.of(2025, 1, 1), LocalDate.of(2025, 12, 31), null, page
        )).isEmpty();
        assertThatThrownBy(() -> reportingService.vouchers(
            company.id(), from, to, null, null, PageRequest.of(0, 20, Sort.by("companyId"))
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("REPORT_SORT_INVALID");
        assertThatThrownBy(() -> reportingService.sales(
            company.id(), to, from, null, page
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("REPORT_FILTER_INVALID");

        Company isolated = companyRepository.saveAndFlush(company("94" + System.nanoTime()));
        assertThat(reportingService.sales(isolated.id(), from, to, null, page)).isEmpty();
    }

    @Test
    void returnsTenantScopedAuditDetailsAndRejectsUnsafeSorting() {
        InvoiceResponse invoice = invoiceService.createSales(
            company.id(),
            actorId,
            request(UUID.randomUUID(), "1", "100", "0", "20")
        );
        invoiceService.approve(company.id(), actorId, invoice.id());

        var audit = auditQueryService.findAll(
            company.id(),
            LocalDate.of(2026, 7, 1),
            LocalDate.now(),
            actorId,
            "INVOICE_APPROVE",
            "INVOICE",
            PageRequest.of(0, 20)
        );

        assertThat(audit.getContent()).singleElement().satisfies(entry -> {
            assertThat(entry.entityId()).isEqualTo(invoice.id());
            assertThat(entry.beforeData()).containsEntry("status", "DRAFT");
            assertThat(entry.afterData()).containsEntry("status", "APPROVED");
        });
        assertThatThrownBy(() -> auditQueryService.findAll(
            company.id(),
            null,
            null,
            null,
            null,
            null,
            PageRequest.of(0, 20, Sort.by("companyId"))
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("AUDIT_SORT_INVALID");

        Company isolated = companyRepository.saveAndFlush(company("95" + System.nanoTime()));
        assertThat(auditQueryService.findAll(
            isolated.id(), null, null, null, null, null, PageRequest.of(0, 20)
        )).isEmpty();
    }

    @Test
    void createsIdempotentPurchaseDraftWithInvoiceBasedCost() {
        UUID idempotencyKey = UUID.randomUUID();
        CreatePurchaseInvoiceRequest request = purchaseRequest(
            idempotencyKey,
            "2",
            "100",
            "10",
            "20"
        );

        InvoiceResponse created = invoiceService.createPurchase(company.id(), actorId, request);
        InvoiceResponse repeated = invoiceService.createPurchase(company.id(), actorId, request);

        assertThat(repeated.id()).isEqualTo(created.id());
        assertThat(created.invoiceNumber()).isEqualTo("AF-2026-000001");
        assertThat(created.invoiceType()).isEqualTo(InvoiceType.PURCHASE);
        assertThat(created.status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(created.subtotal()).isEqualByComparingTo("200.0000");
        assertThat(created.discountTotal()).isEqualByComparingTo("20.0000");
        assertThat(created.taxTotal()).isEqualByComparingTo("36.0000");
        assertThat(created.grandTotal()).isEqualByComparingTo("216.0000");
        assertThat(created.costTotal()).isEqualByComparingTo("180.0000");
        assertThat(created.lines()).singleElement().satisfies(line -> {
            assertThat(line.unitCost()).isEqualByComparingTo("90.0000");
            assertThat(line.costTotal()).isEqualByComparingTo("180.0000");
        });
        assertThat(auditCount(created.id(), "INVOICE_CREATE")).isEqualTo(1);
    }

    @Test
    void approvesPurchaseInvoiceWithAtomicFinancialPostings() {
        InvoiceResponse draft = invoiceService.createPurchase(
            company.id(),
            actorId,
            purchaseRequest(UUID.randomUUID(), "2", "100", "10", "20")
        );

        InvoiceResponse approved = invoiceService.approve(company.id(), actorId, draft.id());
        InvoiceResponse repeated = invoiceService.approve(company.id(), actorId, draft.id());

        assertThat(approved.status()).isEqualTo(InvoiceStatus.APPROVED);
        assertThat(repeated.accountingVoucherId()).isEqualTo(approved.accountingVoucherId());
        assertThat(jdbcTemplate.queryForObject(
            """
            SELECT credit
            FROM current_account_movements
            WHERE company_id = ?
              AND reference_id = ?
            """,
            BigDecimal.class,
            company.id(),
            draft.id()
        )).isEqualByComparingTo("216.0000");
        assertThat(jdbcTemplate.queryForList(
            """
            SELECT chart_account_code
            FROM voucher_lines
            WHERE company_id = ?
              AND voucher_id = ?
            ORDER BY line_number
            """,
            String.class,
            company.id(),
            approved.accountingVoucherId()
        )).containsExactly(
            "153",
            "191",
            supplier.ledgerAccounts().getFirst().fullAccountCode()
        );
        assertThat(jdbcTemplate.queryForObject(
            "SELECT total_debit FROM vouchers WHERE id = ?",
            BigDecimal.class,
            approved.accountingVoucherId()
        )).isEqualByComparingTo("216.0000");
        assertThat(purchaseSourceVoucherCount(draft.id())).isEqualTo(1);
        assertThat(auditCount(draft.id(), "INVOICE_APPROVE")).isEqualTo(1);
    }

    @Test
    void rollsBackPurchaseFinancialEffectsWhenAccountingFails() {
        InvoiceResponse draft = invoiceService.createPurchase(
            company.id(),
            actorId,
            purchaseRequest(UUID.randomUUID(), "2", "100", "0", "20")
        );
        jdbcTemplate.update(
            """
            UPDATE chart_of_accounts
            SET active = false
            WHERE company_id = ?
              AND system_key = 'DEDUCTIBLE_VAT'
            """,
            company.id()
        );

        assertThatThrownBy(() -> invoiceService.approve(company.id(), actorId, draft.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PURCHASE_INVOICE_SYSTEM_ACCOUNTS_MISSING");

        assertThat(invoiceService.get(company.id(), draft.id()).status()).isEqualTo(InvoiceStatus.DRAFT);
        assertThat(count("current_account_movements")).isZero();
        assertThat(purchaseSourceVoucherCount(draft.id())).isZero();
        assertThat(auditCount(draft.id(), "INVOICE_APPROVE")).isZero();
    }

    @Test
    void concurrentPurchaseApprovalCreatesOneSetOfEffects() throws Exception {
        InvoiceResponse draft = invoiceService.createPurchase(
            company.id(),
            actorId,
            purchaseRequest(UUID.randomUUID(), "2", "100", "0", "20")
        );
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Object> outcomes;
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Object>> futures = List.of(
                executor.submit(() -> approveOutcome(ready, start, draft.id())),
                executor.submit(() -> approveOutcome(ready, start, draft.id()))
            );
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            outcomes = futures.stream().map(this::result).toList();
        }

        assertThat(outcomes).allMatch(InvoiceResponse.class::isInstance);
        assertThat(count("current_account_movements")).isEqualTo(1);
        assertThat(purchaseSourceVoucherCount(draft.id())).isEqualTo(1);
        assertThat(auditCount(draft.id(), "INVOICE_APPROVE")).isEqualTo(1);
    }

    private Object approveOutcome(
        CountDownLatch ready,
        CountDownLatch start,
        UUID invoiceId
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out.");
        }
        try {
            return invoiceService.approve(company.id(), actorId, invoiceId);
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private Object settleOutcome(
        CountDownLatch ready,
        CountDownLatch start,
        UUID invoiceId,
        CreateInvoiceSettlementRequest request
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent test start timed out.");
        }
        try {
            return invoiceService.settle(company.id(), actorId, invoiceId, request);
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private Object result(Future<Object> future) {
        try {
            return future.get(15, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }

    private CreateSalesInvoiceRequest request(
        UUID idempotencyKey,
        String quantity,
        String unitPrice,
        String discountRate,
        String vatRate
    ) {
        return request(
            idempotencyKey,
            quantity,
            unitPrice,
            discountRate,
            vatRate,
            LocalDate.of(2026, 8, 17)
        );
    }

    private CreateSalesInvoiceRequest request(
        UUID idempotencyKey,
        String quantity,
        String unitPrice,
        String discountRate,
        String vatRate,
        LocalDate dueDate
    ) {
        return new CreateSalesInvoiceRequest(
            idempotencyKey,
            customer.id(),
            LocalDate.of(2026, 7, 17),
            dueDate,
            "TRY",
            List.of(line(quantity, unitPrice, discountRate, vatRate))
        );
    }

    private LocalDate dashboardDueDate() {
        LocalDate invoiceDate = LocalDate.of(2026, 7, 17);
        LocalDate today = LocalDate.now(ZoneId.of("Europe/Istanbul"));
        LocalDate tomorrow = today.plusDays(1);
        return tomorrow.isAfter(invoiceDate) ? tomorrow : invoiceDate.plusDays(1);
    }

    private CreatePurchaseInvoiceRequest purchaseRequest(
        UUID idempotencyKey,
        String quantity,
        String unitPrice,
        String discountRate,
        String vatRate
    ) {
        return new CreatePurchaseInvoiceRequest(
            idempotencyKey,
            supplier.id(),
            LocalDate.of(2026, 7, 17),
            LocalDate.of(2026, 8, 17),
            "TRY",
            List.of(line(quantity, unitPrice, discountRate, vatRate))
        );
    }

    private CreateInvoiceSettlementRequest settlement(
        UUID idempotencyKey,
        UUID cashAccountId,
        String amount
    ) {
        return new CreateInvoiceSettlementRequest(
            idempotencyKey,
            InvoiceSettlementAccountType.CASH,
            cashAccountId,
            new BigDecimal(amount),
            LocalDate.of(2026, 7, 17),
            "Invoice settlement",
            null
        );
    }

    private InvoiceLineRequest line(
        String quantity,
        String unitPrice,
        String discountRate,
        String vatRate
    ) {
        return new InvoiceLineRequest(
            product.id(),
            null,
            new BigDecimal(quantity),
            new BigDecimal(unitPrice),
            new BigDecimal(discountRate),
            new BigDecimal(vatRate)
        );
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM " + table + " WHERE company_id = ?",
            Integer.class,
            company.id()
        );
    }

    private int sourceVoucherCount(UUID invoiceId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM vouchers
            WHERE company_id = ?
              AND source_type = 'SALES_INVOICE'
              AND source_id = ?
            """,
            Integer.class,
            company.id(),
            invoiceId
        );
    }

    private int purchaseSourceVoucherCount(UUID invoiceId) {
        return jdbcTemplate.queryForObject(
            """
            SELECT count(*)
            FROM vouchers
            WHERE company_id = ?
              AND source_type = 'PURCHASE_INVOICE'
              AND source_id = ?
            """,
            Integer.class,
            company.id(),
            invoiceId
        );
    }

    private int auditCount(UUID entityId, String action) {
        return jdbcTemplate.queryForObject(
            "SELECT count(*) FROM audit_logs WHERE company_id = ? AND entity_id = ? AND action = ?",
            Integer.class,
            company.id(),
            entityId,
            action
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
            VALUES (?, ?, ?, ?, ?, 'Invoice', 'User', 'ACTIVE', false)
            """,
            id,
            companyId,
            "invoice-user-" + id,
            id + "@example.com",
            "$2a$10$abcdefghijklmnopqrstuuABCDEFGHIJKLMNOPQRSTUVWXYZ12345"
        );
        return id;
    }

    private Company company(String source) {
        String digits = source.replaceAll("\\D", "");
        String taxNumber = (digits + "0000000000").substring(0, 10);
        return Company.create(
            "Invoice Test Company " + taxNumber,
            "Invoice Test Company " + taxNumber,
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
