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
import com.yigitcicekci.tillora.voucher.api.request.CreatePaymentVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
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

class PaymentVoucherServiceTest {

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
    private final PaymentVoucherService service = new PaymentVoucherService(
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
    void createsBalancedBankPaymentWithGeneratedPostingLines() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        UUID bankAccountId = UUID.randomUUID();
        UUID payableChartId = UUID.randomUUID();
        UUID bankChartId = UUID.randomUUID();
        CreatePaymentVoucherRequest request = request(
            idempotencyKey,
            currentAccountId,
            SettlementAccountType.BANK,
            bankAccountId,
            "800"
        );
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey))
            .thenReturn(Optional.empty());
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(currentAccountService.findActivePayablePostingAccount(companyId, currentAccountId))
            .thenReturn(new CurrentAccountPostingReference(
                currentAccountId,
                "Supplier",
                payableChartId,
                "320.02.000001"
            ));
        when(financialPostingAccountService.findActiveBankPostingAccount(companyId, bankAccountId))
            .thenReturn(new FinancialPostingAccountReference(
                bankAccountId,
                "Main Bank",
                bankChartId,
                "102.01",
                "TRY"
            ));
        when(chartOfAccountService.findActivePostingAccounts(companyId, List.of(payableChartId, bankChartId)))
            .thenReturn(List.of(
                new PostingAccountReference(payableChartId, "320.02.000001", "Supplier"),
                new PostingAccountReference(bankChartId, "102.01", "Main Bank")
            ));
        when(voucherNumberGenerator.next(companyId, VoucherType.PAYMENT, 2026))
            .thenReturn("TDI-2026-000001");
        when(voucherRepository.saveAndFlush(any(Voucher.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(voucherLineRepository.saveAllAndFlush(anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        VoucherResponse response = service.create(companyId, actorId, request);

        assertThat(response.voucherNumber()).isEqualTo("TDI-2026-000001");
        assertThat(response.voucherType()).isEqualTo(VoucherType.PAYMENT);
        assertThat(response.idempotencyKey()).isEqualTo(idempotencyKey);
        assertThat(response.totalDebit()).isEqualByComparingTo("800.0000");
        assertThat(response.totalCredit()).isEqualByComparingTo("800.0000");
        assertThat(response.lines()).hasSize(2);
        assertThat(response.lines().get(0).accountCode()).isEqualTo("320.02.000001");
        assertThat(response.lines().get(0).debit()).isEqualByComparingTo("800.0000");
        assertThat(response.lines().get(0).currentAccountId()).isEqualTo(currentAccountId);
        assertThat(response.lines().get(1).accountCode()).isEqualTo("102.01");
        assertThat(response.lines().get(1).credit()).isEqualByComparingTo("800.0000");
        assertThat(response.lines().get(1).currentAccountId()).isNull();
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
        UUID bankAccountId = UUID.randomUUID();
        CreatePaymentVoucherRequest request = request(
            idempotencyKey,
            currentAccountId,
            SettlementAccountType.BANK,
            bankAccountId,
            "800"
        );
        Voucher existing = Voucher.createGuided(
            companyId,
            "TDI-2026-000001",
            VoucherType.PAYMENT,
            LocalDate.of(2026, 7, 16),
            "Payment",
            "PAY-1",
            "TRY",
            new BigDecimal("1.00000000"),
            actorId,
            new BigDecimal("800.0000"),
            new BigDecimal("800.0000"),
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
    void rejectsIdempotencyKeyUsedForCollection() {
        UUID companyId = UUID.randomUUID();
        UUID idempotencyKey = UUID.randomUUID();
        CreatePaymentVoucherRequest request = request(
            idempotencyKey,
            UUID.randomUUID(),
            SettlementAccountType.CASH,
            UUID.randomUUID(),
            "500"
        );
        Voucher existing = Voucher.createGuided(
            companyId,
            "THS-2026-000001",
            VoucherType.COLLECTION,
            LocalDate.of(2026, 7, 16),
            "Payment",
            "PAY-1",
            "TRY",
            new BigDecimal("1.00000000"),
            UUID.randomUUID(),
            new BigDecimal("500.0000"),
            new BigDecimal("500.0000"),
            idempotencyKey,
            fingerprint(request)
        );
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, idempotencyKey))
            .thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.create(companyId, UUID.randomUUID(), request))
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
        when(currentAccountService.findActivePayablePostingAccount(companyId, currentAccountId))
            .thenReturn(new CurrentAccountPostingReference(
                currentAccountId,
                "Supplier",
                UUID.randomUUID(),
                "320.01.000001"
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
            request(idempotencyKey, currentAccountId, SettlementAccountType.CASH, cashAccountId, "500")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("PAYMENT_ACCOUNT_CURRENCY_MISMATCH");
        verify(voucherNumberGenerator, never()).next(any(), any(), anyInt());
        verifyNoInteractions(chartOfAccountService, auditLogService);
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
            "  Payment  ",
            "  PAY-1  "
        );
    }

    private String fingerprint(CreatePaymentVoucherRequest request) {
        return GuidedVoucherRequestFingerprint.create(
            request.currentAccountId(),
            request.settlementAccountType(),
            request.settlementAccountId(),
            request.amount().setScale(4),
            request.voucherDate(),
            request.movementNote().trim(),
            request.documentNumber().trim()
        );
    }
}
