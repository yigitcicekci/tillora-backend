package com.yigitcicekci.tillora.voucher.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.chartofaccount.application.service.ChartOfAccountService;
import com.yigitcicekci.tillora.chartofaccount.application.service.PostingAccountReference;
import com.yigitcicekci.tillora.company.application.service.CompanyFinancialContext;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountPostingReference;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
import com.yigitcicekci.tillora.finance.application.service.FinancialPostingAccountReference;
import com.yigitcicekci.tillora.finance.application.service.FinancialPostingAccountService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.voucher.api.request.CreateCollectionVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
import com.yigitcicekci.tillora.voucher.domain.entity.VoucherLine;
import com.yigitcicekci.tillora.voucher.domain.enumeration.SettlementAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherLineRepository;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherRepository;
import com.yigitcicekci.tillora.voucher.infrastructure.persistence.VoucherIdempotencyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CollectionVoucherServiceTest {

    private final VoucherRepository voucherRepository = mock(VoucherRepository.class);
    private final VoucherLineRepository voucherLineRepository = mock(VoucherLineRepository.class);
    private final VoucherNumberGenerator voucherNumberGenerator = mock(VoucherNumberGenerator.class);
    private final VoucherIdempotencyRepository voucherIdempotencyRepository =
        mock(VoucherIdempotencyRepository.class);
    private final CurrentAccountService currentAccountService = mock(CurrentAccountService.class);
    private final FinancialPostingAccountService financialPostingAccountService =
        mock(FinancialPostingAccountService.class);
    private final ChartOfAccountService chartOfAccountService = mock(ChartOfAccountService.class);
    private final CompanyService companyService = mock(CompanyService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final CollectionVoucherService service = new CollectionVoucherService(
        voucherRepository,
        voucherLineRepository,
        voucherNumberGenerator,
        voucherIdempotencyRepository,
        currentAccountService,
        financialPostingAccountService,
        chartOfAccountService,
        companyService,
        auditLogService
    );

    @Test
    void createsBalancedCashCollectionWithGeneratedPostingLines() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        UUID cashAccountId = UUID.randomUUID();
        UUID receivableChartId = UUID.randomUUID();
        UUID cashChartId = UUID.randomUUID();
        LocalDate date = LocalDate.of(2026, 7, 16);
        CreateCollectionVoucherRequest request = request(
            idempotencyKey,
            currentAccountId,
            SettlementAccountType.CASH,
            cashAccountId,
            "100"
        );
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey))
            .thenReturn(Optional.empty());
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(currentAccountService.findActiveReceivablePostingAccount(companyId, currentAccountId))
            .thenReturn(new CurrentAccountPostingReference(
                currentAccountId,
                "Customer",
                receivableChartId,
                "120.01.000001"
            ));
        when(financialPostingAccountService.findActiveCashPostingAccount(companyId, cashAccountId))
            .thenReturn(new FinancialPostingAccountReference(
                cashAccountId,
                "Main Cash",
                cashChartId,
                "100.01",
                "TRY"
            ));
        when(chartOfAccountService.findActivePostingAccounts(companyId, List.of(cashChartId, receivableChartId)))
            .thenReturn(List.of(
                new PostingAccountReference(cashChartId, "100.01", "Main Cash"),
                new PostingAccountReference(receivableChartId, "120.01.000001", "Customer")
            ));
        when(voucherNumberGenerator.next(companyId, VoucherType.COLLECTION, 2026))
            .thenReturn("THS-2026-000001");
        when(voucherRepository.saveAndFlush(any(Voucher.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(voucherLineRepository.saveAllAndFlush(anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        VoucherResponse response = service.create(companyId, actorId, request);

        assertThat(response.voucherNumber()).isEqualTo("THS-2026-000001");
        assertThat(response.voucherType()).isEqualTo(VoucherType.COLLECTION);
        assertThat(response.voucherDate()).isEqualTo(date);
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(response.totalDebit()).isEqualByComparingTo("100.0000");
        assertThat(response.totalCredit()).isEqualByComparingTo("100.0000");
        assertThat(response.lines()).hasSize(2);
        assertThat(response.lines().get(0).accountCode()).isEqualTo("100.01");
        assertThat(response.lines().get(0).debit()).isEqualByComparingTo("100.0000");
        assertThat(response.lines().get(0).currentAccountId()).isNull();
        assertThat(response.lines().get(1).accountCode()).isEqualTo("120.01.000001");
        assertThat(response.lines().get(1).credit()).isEqualByComparingTo("100.0000");
        assertThat(response.lines().get(1).currentAccountId()).isEqualTo(currentAccountId);
        verify(voucherIdempotencyRepository).acquire(companyId, idempotencyKey);
        verify(auditLogService).record(
            companyId,
            actorId,
            AuditAction.VOUCHER_CREATE,
            "VOUCHER",
            response.id()
        );
    }

    @Test
    void returnsExistingVoucherForSameIdempotentRequestWithoutNewSideEffects() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        UUID cashAccountId = UUID.randomUUID();
        CreateCollectionVoucherRequest request = request(
            idempotencyKey,
            currentAccountId,
            SettlementAccountType.CASH,
            cashAccountId,
            "100"
        );
        Voucher existing = Voucher.createGuided(
            companyId,
            "THS-2026-000001",
            VoucherType.COLLECTION,
            LocalDate.of(2026, 7, 16),
            "Collection",
            "DOC-1",
            "TRY",
            new BigDecimal("1.00000000"),
            actorId,
            new BigDecimal("100.0000"),
            new BigDecimal("100.0000"),
            idempotencyKey,
            fingerprint(request)
        );
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey))
            .thenReturn(Optional.of(existing));
        when(voucherLineRepository.findByVoucherIdAndCompanyIdOrderByLineNumberAsc(existing.id(), companyId))
            .thenReturn(List.of());

        VoucherResponse response = service.create(companyId, actorId, request);

        assertThat(response.id()).isEqualTo(existing.id());
        verify(voucherNumberGenerator, never()).next(any(), any(), anyInt());
        verifyNoInteractions(
            currentAccountService,
            financialPostingAccountService,
            chartOfAccountService,
            companyService,
            auditLogService
        );
    }

    @Test
    void rejectsIdempotencyKeyReusedWithDifferentRequest() {
        UUID companyId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        Voucher existing = Voucher.createGuided(
            companyId,
            "THS-2026-000001",
            VoucherType.COLLECTION,
            LocalDate.of(2026, 7, 16),
            "Collection",
            "DOC-1",
            "TRY",
            new BigDecimal("1.00000000"),
            UUID.randomUUID(),
            new BigDecimal("100.0000"),
            new BigDecimal("100.0000"),
            idempotencyKey,
            "a".repeat(64)
        );
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(
            companyId,
            UUID.randomUUID(),
            request(
                idempotencyKey,
                UUID.randomUUID(),
                SettlementAccountType.BANK,
                UUID.randomUUID(),
                "200"
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("IDEMPOTENCY_KEY_REUSED");
        verifyNoInteractions(
            voucherNumberGenerator,
            currentAccountService,
            financialPostingAccountService,
            chartOfAccountService,
            companyService,
            auditLogService
        );
    }

    @Test
    void rejectsSettlementCurrencyMismatchBeforeNumbering() {
        UUID companyId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        UUID cashAccountId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey))
            .thenReturn(Optional.empty());
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(currentAccountService.findActiveReceivablePostingAccount(companyId, currentAccountId))
            .thenReturn(new CurrentAccountPostingReference(
                currentAccountId,
                "Customer",
                UUID.randomUUID(),
                "120.01.000001"
            ));
        when(financialPostingAccountService.findActiveCashPostingAccount(companyId, cashAccountId))
            .thenReturn(new FinancialPostingAccountReference(
                cashAccountId,
                "Dollar Cash",
                UUID.randomUUID(),
                "100.01",
                "USD"
            ));

        assertThatThrownBy(() -> service.create(
            companyId,
            UUID.randomUUID(),
            request(idempotencyKey, currentAccountId, SettlementAccountType.CASH, cashAccountId, "100")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("COLLECTION_ACCOUNT_CURRENCY_MISMATCH");
        verify(voucherNumberGenerator, never()).next(any(), any(), anyInt());
        verifyNoInteractions(chartOfAccountService, auditLogService);
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
            "  Collection  ",
            "  DOC-1  "
        );
    }

    private String fingerprint(CreateCollectionVoucherRequest request) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            String canonical = String.join(
                "|",
                request.currentAccountId().toString(),
                request.settlementAccountType().name(),
                request.settlementAccountId().toString(),
                request.amount().setScale(4).toPlainString(),
                request.voucherDate().toString(),
                request.movementNote().trim(),
                request.documentNumber().trim()
            );
            return java.util.HexFormat.of().formatHex(
                digest.digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8))
            );
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }
}
