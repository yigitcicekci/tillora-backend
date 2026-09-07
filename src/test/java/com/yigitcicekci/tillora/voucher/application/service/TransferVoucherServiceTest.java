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
import com.yigitcicekci.tillora.voucher.api.request.CreateTransferVoucherRequest;
import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferSourceAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferTargetAccountType;
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

class TransferVoucherServiceTest {

    private final VoucherRepository voucherRepository = mock(VoucherRepository.class);
    private final VoucherLineRepository voucherLineRepository = mock(VoucherLineRepository.class);
    private final VoucherNumberGenerator voucherNumberGenerator = mock(VoucherNumberGenerator.class);
    private final VoucherIdempotencyRepository voucherIdempotencyRepository =
        mock(VoucherIdempotencyRepository.class);
    private final FinancialPostingAccountService financialPostingAccountService =
        mock(FinancialPostingAccountService.class);
    private final CurrentAccountService currentAccountService = mock(CurrentAccountService.class);
    private final ChartOfAccountService chartOfAccountService = mock(ChartOfAccountService.class);
    private final CompanyService companyService = mock(CompanyService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final TransferVoucherService service = new TransferVoucherService(
        voucherRepository,
        voucherLineRepository,
        voucherNumberGenerator,
        voucherIdempotencyRepository,
        financialPostingAccountService,
        currentAccountService,
        chartOfAccountService,
        companyService,
        auditLogService
    );

    @Test
    void createsBalancedCashToBankTransfer() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID cashId = UUID.randomUUID();
        UUID bankId = UUID.randomUUID();
        UUID cashChartId = UUID.randomUUID();
        UUID bankChartId = UUID.randomUUID();
        CreateTransferVoucherRequest request = request(cashId, bankId, "250");
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, request.idempotencyKey()))
            .thenReturn(Optional.empty());
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(financialPostingAccountService.findActiveCashPostingAccount(companyId, cashId))
            .thenReturn(account(cashId, cashChartId, "100.01", "Cash", "TRY"));
        when(financialPostingAccountService.findActiveBankPostingAccount(companyId, bankId))
            .thenReturn(account(bankId, bankChartId, "102.01", "Bank", "TRY"));
        when(chartOfAccountService.findActivePostingAccounts(companyId, List.of(cashChartId, bankChartId)))
            .thenReturn(List.of(
                new PostingAccountReference(cashChartId, "100.01", "Cash"),
                new PostingAccountReference(bankChartId, "102.01", "Bank")
            ));
        when(voucherNumberGenerator.next(companyId, VoucherType.TRANSFER, 2026))
            .thenReturn("VRM-2026-000001");
        when(voucherRepository.saveAndFlush(any(Voucher.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(voucherLineRepository.saveAllAndFlush(anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(companyId, actorId, request);

        assertThat(response.voucherType()).isEqualTo(VoucherType.TRANSFER);
        assertThat(response.voucherNumber()).isEqualTo("VRM-2026-000001");
        assertThat(response.totalDebit()).isEqualByComparingTo("250.0000");
        assertThat(response.totalCredit()).isEqualByComparingTo("250.0000");
        assertThat(response.lines()).extracting(line -> line.accountCode())
            .containsExactly("102.01", "100.01");
        assertThat(response.lines().getFirst().debit()).isEqualByComparingTo("250.0000");
        assertThat(response.lines().getLast().credit()).isEqualByComparingTo("250.0000");
        verify(auditLogService).record(
            companyId, actorId, AuditAction.VOUCHER_TRANSFER_CREATE, "VOUCHER", response.id()
        );
    }

    @Test
    void createsBalancedCustomerToBankTransfer() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID bankId = UUID.randomUUID();
        UUID customerChartId = UUID.randomUUID();
        UUID bankChartId = UUID.randomUUID();
        CreateTransferVoucherRequest request = new CreateTransferVoucherRequest(
            UUID.randomUUID(),
            TransferSourceAccountType.CUSTOMER,
            customerId,
            TransferTargetAccountType.BANK,
            bankId,
            new BigDecimal("250"),
            LocalDate.of(2026, 7, 16),
            "Transfer",
            "DOC-1"
        );
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, request.idempotencyKey()))
            .thenReturn(Optional.empty());
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(currentAccountService.findActiveTransferReceivablePostingAccount(companyId, customerId))
            .thenReturn(new CurrentAccountPostingReference(
                customerId, "Customer", customerChartId, "120.01.000001"
            ));
        when(financialPostingAccountService.findActiveBankPostingAccount(companyId, bankId))
            .thenReturn(account(bankId, bankChartId, "102.01", "Bank", "TRY"));
        when(chartOfAccountService.findActivePostingAccounts(
            companyId, List.of(customerChartId, bankChartId)
        )).thenReturn(List.of(
            new PostingAccountReference(customerChartId, "120.01.000001", "Customer"),
            new PostingAccountReference(bankChartId, "102.01", "Bank")
        ));
        when(voucherNumberGenerator.next(companyId, VoucherType.TRANSFER, 2026))
            .thenReturn("VRM-2026-000001");
        when(voucherRepository.saveAndFlush(any(Voucher.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(voucherLineRepository.saveAllAndFlush(anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(companyId, actorId, request);

        assertThat(response.lines()).extracting(line -> line.accountCode())
            .containsExactly("102.01", "120.01.000001");
        assertThat(response.lines().getFirst().debit()).isEqualByComparingTo("250.0000");
        assertThat(response.lines().getLast().credit()).isEqualByComparingTo("250.0000");
        assertThat(response.lines().getLast().currentAccountId()).isEqualTo(customerId);
    }

    @Test
    void createsBalancedSupplierToSupplierTransfer() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID sourceSupplierId = UUID.randomUUID();
        UUID targetSupplierId = UUID.randomUUID();
        UUID sourceChartId = UUID.randomUUID();
        UUID targetChartId = UUID.randomUUID();
        CreateTransferVoucherRequest request = new CreateTransferVoucherRequest(
            UUID.randomUUID(),
            TransferSourceAccountType.SUPPLIER,
            sourceSupplierId,
            TransferTargetAccountType.SUPPLIER,
            targetSupplierId,
            new BigDecimal("250"),
            LocalDate.of(2026, 7, 16),
            "Transfer",
            "DOC-1"
        );
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, request.idempotencyKey()))
            .thenReturn(Optional.empty());
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(currentAccountService.findActiveTransferPayablePostingAccount(companyId, sourceSupplierId))
            .thenReturn(new CurrentAccountPostingReference(
                sourceSupplierId, "Source Supplier", sourceChartId, "320.01.000001"
            ));
        when(currentAccountService.findActiveTransferPayablePostingAccount(companyId, targetSupplierId))
            .thenReturn(new CurrentAccountPostingReference(
                targetSupplierId, "Target Supplier", targetChartId, "320.01.000002"
            ));
        when(chartOfAccountService.findActivePostingAccounts(companyId, List.of(sourceChartId, targetChartId)))
            .thenReturn(List.of(
                new PostingAccountReference(sourceChartId, "320.01.000001", "Source Supplier"),
                new PostingAccountReference(targetChartId, "320.01.000002", "Target Supplier")
            ));
        when(voucherNumberGenerator.next(companyId, VoucherType.TRANSFER, 2026))
            .thenReturn("VRM-2026-000001");
        when(voucherRepository.saveAndFlush(any(Voucher.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(voucherLineRepository.saveAllAndFlush(anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(companyId, actorId, request);

        assertThat(response.lines()).extracting(line -> line.accountCode())
            .containsExactly("320.01.000002", "320.01.000001");
        assertThat(response.lines()).extracting(line -> line.currentAccountId())
            .containsExactly(targetSupplierId, sourceSupplierId);
        assertThat(response.lines().getFirst().debit()).isEqualByComparingTo("250.0000");
        assertThat(response.lines().getLast().credit()).isEqualByComparingTo("250.0000");
    }

    @Test
    void rejectsSameSourceAndTargetBeforeLocking() {
        UUID accountId = UUID.randomUUID();

        assertThatThrownBy(() -> service.create(
            UUID.randomUUID(), UUID.randomUUID(),
            new CreateTransferVoucherRequest(
                UUID.randomUUID(),
                TransferSourceAccountType.CASH,
                accountId,
                TransferTargetAccountType.CASH,
                accountId,
                BigDecimal.ONE,
                null,
                null,
                null
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("TRANSFER_ACCOUNTS_MUST_DIFFER");
    }

    @Test
    void returnsExistingVoucherForIdenticalIdempotentRequest() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID cashId = UUID.randomUUID();
        UUID bankId = UUID.randomUUID();
        CreateTransferVoucherRequest request = request(cashId, bankId, "250");
        Voucher existing = Voucher.createGuided(
            companyId,
            "VRM-2026-000001",
            VoucherType.TRANSFER,
            request.voucherDate(),
            request.movementNote(),
            request.documentNumber(),
            "TRY",
            new BigDecimal("1.00000000"),
            actorId,
            new BigDecimal("250.0000"),
            new BigDecimal("250.0000"),
            request.idempotencyKey(),
            GuidedVoucherRequestFingerprint.transfer(
                request.sourceAccountType(),
                request.sourceAccountId(),
                request.targetAccountType(),
                request.targetAccountId(),
                request.amount().setScale(4),
                request.voucherDate(),
                request.movementNote(),
                request.documentNumber()
            )
        );
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, request.idempotencyKey()))
            .thenReturn(Optional.of(existing));
        when(voucherLineRepository.findByVoucherIdAndCompanyIdOrderByLineNumberAsc(existing.id(), companyId))
            .thenReturn(List.of());

        var response = service.create(companyId, actorId, request);

        assertThat(response.id()).isEqualTo(existing.id());
        verify(voucherNumberGenerator, never()).next(any(), any(), anyInt());
        verifyNoInteractions(
            financialPostingAccountService, chartOfAccountService, companyService, auditLogService
        );
    }

    @Test
    void rejectsCurrencyMismatchBeforeNumbering() {
        UUID companyId = UUID.randomUUID();
        UUID cashId = UUID.randomUUID();
        UUID bankId = UUID.randomUUID();
        CreateTransferVoucherRequest request = request(cashId, bankId, "250");
        when(voucherRepository.findByCompanyIdAndIdempotencyKey(companyId, request.idempotencyKey()))
            .thenReturn(Optional.empty());
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(financialPostingAccountService.findActiveCashPostingAccount(companyId, cashId))
            .thenReturn(account(cashId, UUID.randomUUID(), "100.01", "Cash", "TRY"));
        when(financialPostingAccountService.findActiveBankPostingAccount(companyId, bankId))
            .thenReturn(account(bankId, UUID.randomUUID(), "102.01", "Bank", "USD"));

        assertThatThrownBy(() -> service.create(companyId, UUID.randomUUID(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("TRANSFER_ACCOUNT_CURRENCY_MISMATCH");
        verify(voucherNumberGenerator, never()).next(any(), any(), anyInt());
    }

    private CreateTransferVoucherRequest request(UUID cashId, UUID bankId, String amount) {
        return new CreateTransferVoucherRequest(
            UUID.randomUUID(),
            TransferSourceAccountType.CASH,
            cashId,
            TransferTargetAccountType.BANK,
            bankId,
            new BigDecimal(amount),
            LocalDate.of(2026, 7, 16),
            "Transfer",
            "DOC-1"
        );
    }

    private FinancialPostingAccountReference account(
        UUID id, UUID chartId, String code, String name, String currency
    ) {
        return new FinancialPostingAccountReference(id, name, chartId, code, currency);
    }
}
