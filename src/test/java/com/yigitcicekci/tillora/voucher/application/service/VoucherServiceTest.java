package com.yigitcicekci.tillora.voucher.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.chartofaccount.application.service.ChartOfAccountService;
import com.yigitcicekci.tillora.chartofaccount.application.service.PostingAccountReference;
import com.yigitcicekci.tillora.company.application.service.CompanyFinancialContext;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.finance.application.service.FinancialPostingAccountService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.voucher.api.request.CreateVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.UpdateVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.VoucherLineRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
import com.yigitcicekci.tillora.voucher.domain.entity.VoucherLine;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherLineRepository;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherRepository;
import com.yigitcicekci.tillora.voucher.infrastructure.persistence.VoucherIdempotencyRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VoucherServiceTest {

    private final VoucherRepository voucherRepository = mock(VoucherRepository.class);
    private final VoucherLineRepository voucherLineRepository = mock(VoucherLineRepository.class);
    private final VoucherNumberGenerator voucherNumberGenerator = mock(VoucherNumberGenerator.class);
    private final VoucherIdempotencyRepository voucherIdempotencyRepository =
        mock(VoucherIdempotencyRepository.class);
    private final FinancialPostingAccountService financialPostingAccountService =
        mock(FinancialPostingAccountService.class);
    private final ChartOfAccountService chartOfAccountService = mock(ChartOfAccountService.class);
    private final CompanyService companyService = mock(CompanyService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final VoucherService service = new VoucherService(
        voucherRepository,
        voucherLineRepository,
        voucherNumberGenerator,
        voucherIdempotencyRepository,
        financialPostingAccountService,
        chartOfAccountService,
        companyService,
        auditLogService
    );

    @BeforeEach
    void defaultCashBalanceCheck() {
        when(financialPostingAccountService.lockCashAccountsForBalance(any(), anyCollection()))
            .thenReturn(Set.of());
    }

    @Test
    void rejectsNonOffsetManualVoucherBeforeUsingDependencies() {
        CreateVoucherRequest request = createRequest(
            VoucherType.COLLECTION,
            UUID.randomUUID(),
            UUID.randomUUID()
        );

        assertThatThrownBy(() -> service.create(UUID.randomUUID(), UUID.randomUUID(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("MANUAL_VOUCHER_TYPE_NOT_SUPPORTED");
        verifyNoInteractions(
            voucherRepository,
            voucherLineRepository,
            voucherNumberGenerator,
            voucherIdempotencyRepository,
            chartOfAccountService,
            companyService,
            auditLogService
        );
    }

    @Test
    void rejectsLineContainingBothDebitAndCreditBeforeNumbering() {
        UUID companyId = UUID.randomUUID();
        UUID firstAccountId = UUID.randomUUID();
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        CreateVoucherRequest request = new CreateVoucherRequest(
            UUID.randomUUID(),
            VoucherType.OFFSET,
            LocalDate.of(2026, 7, 16),
            null,
            null,
            null,
            List.of(
                new VoucherLineRequest(
                    firstAccountId,
                    null,
                    new BigDecimal("10.0000"),
                    new BigDecimal("5.0000"),
                    null,
                    null
                ),
                creditLine(UUID.randomUUID(), "5.0000")
            )
        );

        assertThatThrownBy(() -> service.create(companyId, UUID.randomUUID(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_LINE_SIDE_INVALID");
        verifyNoInteractions(
            voucherRepository,
            voucherLineRepository,
            voucherNumberGenerator,
            chartOfAccountService,
            auditLogService
        );
    }

    @Test
    void rejectsMissingOrInactivePostingAccountBeforeNumbering() {
        UUID companyId = UUID.randomUUID();
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(chartOfAccountService.findActivePostingAccounts(any(), anyCollection()))
            .thenReturn(List.of(new PostingAccountReference(debitAccountId, "100.01", "Main Cash")));
        CreateVoucherRequest request = createRequest(
            VoucherType.OFFSET,
            debitAccountId,
            creditAccountId
        );

        assertThatThrownBy(() -> service.create(
            companyId,
            UUID.randomUUID(),
            request
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_POSTING_ACCOUNT_INVALID");
        verify(voucherNumberGenerator, never()).next(any(), any(), anyInt());
        verify(voucherIdempotencyRepository).acquire(companyId, request.idempotencyKey());
        verify(voucherRepository).findByCompanyIdAndIdempotencyKey(
            companyId,
            request.idempotencyKey()
        );
        verifyNoInteractions(voucherLineRepository, auditLogService);
    }

    @Test
    void normalizesDraftAssignsNumberAndAuditsCreation() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        LocalDate voucherDate = LocalDate.of(2026, 7, 16);
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(chartOfAccountService.findActivePostingAccounts(any(), anyCollection()))
            .thenReturn(List.of(
                new PostingAccountReference(debitAccountId, "100.01", "Main Cash"),
                new PostingAccountReference(creditAccountId, "320.01.000001", "Supplier")
            ));
        when(voucherNumberGenerator.next(companyId, VoucherType.OFFSET, 2026))
            .thenReturn("MHS-2026-000001");
        when(voucherRepository.saveAndFlush(any(Voucher.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(voucherLineRepository.saveAllAndFlush(anyList()))
            .thenAnswer(invocation -> invocation.getArgument(0));
        CreateVoucherRequest request = new CreateVoucherRequest(
            UUID.randomUUID(),
            VoucherType.OFFSET,
            voucherDate,
            "  Opening balance  ",
            "  DOC-1  ",
            "try",
            List.of(
                new VoucherLineRequest(
                    debitAccountId,
                    "  Cash movement  ",
                    new BigDecimal("125"),
                    BigDecimal.ZERO,
                    new BigDecimal("2"),
                    voucherDate.plusDays(10)
                ),
                new VoucherLineRequest(
                    creditAccountId,
                    "   ",
                    BigDecimal.ZERO,
                    new BigDecimal("125.0"),
                    null,
                    null
                )
            )
        );

        VoucherResponse response = service.create(companyId, actorId, request);

        assertThat(response.voucherNumber()).isEqualTo("MHS-2026-000001");
        assertThat(response.voucherType()).isEqualTo(VoucherType.OFFSET);
        assertThat(response.voucherDate()).isEqualTo(voucherDate);
        assertThat(response.movementNote()).isEqualTo("Opening balance");
        assertThat(response.documentNumber()).isEqualTo("DOC-1");
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.exchangeRate()).isEqualByComparingTo("1.00000000");
        assertThat(response.totalDebit()).isEqualByComparingTo("125.0000");
        assertThat(response.totalCredit()).isEqualByComparingTo("125.0000");
        assertThat(response.status()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(response.createdBy()).isEqualTo(actorId);
        assertThat(response.idempotencyKey()).isEqualTo(request.idempotencyKey());
        assertThat(response.lines()).hasSize(2);
        assertThat(response.lines().get(0).lineNumber()).isEqualTo(1);
        assertThat(response.lines().get(0).accountCode()).isEqualTo("100.01");
        assertThat(response.lines().get(0).movementNote()).isEqualTo("Cash movement");
        assertThat(response.lines().get(0).debit()).isEqualByComparingTo("125.0000");
        assertThat(response.lines().get(0).quantity()).isEqualByComparingTo("2.000000");
        assertThat(response.lines().get(1).lineNumber()).isEqualTo(2);
        assertThat(response.lines().get(1).accountName()).isEqualTo("Supplier");
        assertThat(response.lines().get(1).movementNote()).isNull();
        verify(voucherNumberGenerator).next(companyId, VoucherType.OFFSET, 2026);
        verify(auditLogService).record(
            companyId,
            actorId,
            AuditAction.VOUCHER_CREATE,
            "VOUCHER",
            response.id()
        );
    }

    @Test
    void rejectsUnbalancedApprovalWithoutChangingVoucherOrAuditing() {
        UUID companyId = UUID.randomUUID();
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        Voucher voucher = voucher(
            companyId,
            "MHS-2026-000001",
            new BigDecimal("100.0000"),
            new BigDecimal("90.0000")
        );
        List<VoucherLine> lines = voucherLines(
            companyId,
            voucher.id(),
            debitAccountId,
            creditAccountId,
            "100.0000",
            "90.0000"
        );
        when(voucherRepository.findForUpdate(voucher.id(), companyId)).thenReturn(Optional.of(voucher));
        when(voucherLineRepository.findByVoucherIdAndCompanyIdOrderByLineNumberAsc(voucher.id(), companyId))
            .thenReturn(lines);
        when(chartOfAccountService.findActivePostingAccounts(any(), anyCollection()))
            .thenReturn(postingReferences(debitAccountId, creditAccountId));

        assertThatThrownBy(() -> service.approve(companyId, UUID.randomUUID(), voucher.id()))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_NOT_BALANCED");
        assertThat(voucher.status()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(voucher.approvedAt()).isNull();
        verify(voucherRepository, never()).saveAndFlush(any(Voucher.class));
        verifyNoInteractions(auditLogService);
    }

    @Test
    void repeatedApprovalIsIdempotentAndAuditedOnce() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        Voucher voucher = voucher(
            companyId,
            "MHS-2026-000001",
            new BigDecimal("100.0000"),
            new BigDecimal("100.0000")
        );
        List<VoucherLine> lines = voucherLines(
            companyId,
            voucher.id(),
            debitAccountId,
            creditAccountId,
            "100.0000",
            "100.0000"
        );
        when(voucherRepository.findForUpdate(voucher.id(), companyId)).thenReturn(Optional.of(voucher));
        when(voucherLineRepository.findByVoucherIdAndCompanyIdOrderByLineNumberAsc(voucher.id(), companyId))
            .thenReturn(lines);
        when(chartOfAccountService.findActivePostingAccounts(any(), anyCollection()))
            .thenReturn(postingReferences(debitAccountId, creditAccountId));
        when(voucherRepository.saveAndFlush(voucher)).thenReturn(voucher);

        VoucherResponse first = service.approve(companyId, actorId, voucher.id());
        VoucherResponse second = service.approve(companyId, actorId, voucher.id());

        assertThat(first.status()).isEqualTo(VoucherStatus.APPROVED);
        assertThat(second.status()).isEqualTo(VoucherStatus.APPROVED);
        assertThat(second.approvedBy()).isEqualTo(actorId);
        assertThat(second.approvedAt()).isEqualTo(first.approvedAt());
        verify(voucherRepository, times(1)).saveAndFlush(voucher);
        verify(chartOfAccountService, times(1))
            .findActivePostingAccounts(any(), anyCollection());
        verify(auditLogService, times(1)).record(
            companyId,
            actorId,
            AuditAction.VOUCHER_APPROVE,
            "VOUCHER",
            voucher.id()
        );
    }

    @Test
    void repeatedCancellationPreservesApprovalAndAuditsOnce() {
        UUID companyId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID cancellingActorId = UUID.randomUUID();
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        Voucher voucher = voucher(
            companyId,
            "MHS-2026-000001",
            new BigDecimal("100.0000"),
            new BigDecimal("100.0000")
        );
        voucher.approve(approverId);
        var approvedAt = voucher.approvedAt();
        List<VoucherLine> lines = voucherLines(
            companyId,
            voucher.id(),
            debitAccountId,
            creditAccountId,
            "100.0000",
            "100.0000"
        );
        when(voucherRepository.findForUpdate(voucher.id(), companyId)).thenReturn(Optional.of(voucher));
        when(voucherLineRepository.findByVoucherIdAndCompanyIdOrderByLineNumberAsc(voucher.id(), companyId))
            .thenReturn(lines);
        when(voucherRepository.saveAndFlush(voucher)).thenReturn(voucher);

        VoucherResponse first = service.cancel(companyId, cancellingActorId, voucher.id(), "  Incorrect entry  ");
        VoucherResponse second = service.cancel(companyId, cancellingActorId, voucher.id(), "Incorrect entry");

        assertThat(first.status()).isEqualTo(VoucherStatus.CANCELLED);
        assertThat(second.status()).isEqualTo(VoucherStatus.CANCELLED);
        assertThat(second.approvedBy()).isEqualTo(approverId);
        assertThat(second.approvedAt()).isEqualTo(approvedAt);
        assertThat(second.cancelledBy()).isEqualTo(cancellingActorId);
        assertThat(second.cancellationReason()).isEqualTo("Incorrect entry");
        assertThat(second.lines()).extracting(line -> line.id())
            .containsExactlyElementsOf(first.lines().stream().map(line -> line.id()).toList());
        verify(voucherRepository, times(1)).saveAndFlush(voucher);
        verify(auditLogService, times(1)).record(
            companyId,
            cancellingActorId,
            AuditAction.VOUCHER_CANCEL,
            "VOUCHER",
            voucher.id()
        );
    }

    @Test
    void rejectsUpdatingApprovedVoucherBeforeResolvingNewDraftData() {
        UUID companyId = UUID.randomUUID();
        Voucher voucher = voucher(
            companyId,
            "MHS-2026-000001",
            new BigDecimal("100.0000"),
            new BigDecimal("100.0000")
        );
        voucher.approve(UUID.randomUUID());
        when(voucherRepository.findForUpdate(voucher.id(), companyId)).thenReturn(Optional.of(voucher));
        UpdateVoucherRequest request = new UpdateVoucherRequest(
            LocalDate.of(2026, 7, 16),
            "Updated",
            null,
            "TRY",
            balancedLines(UUID.randomUUID(), UUID.randomUUID())
        );

        assertThatThrownBy(() -> service.update(companyId, UUID.randomUUID(), voucher.id(), request))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("VOUCHER_NOT_DRAFT");
        verify(voucherRepository, never()).saveAndFlush(any(Voucher.class));
        verifyNoInteractions(
            voucherLineRepository,
            voucherNumberGenerator,
            chartOfAccountService,
            companyService,
            auditLogService
        );
    }

    private CreateVoucherRequest createRequest(
        VoucherType type,
        UUID debitAccountId,
        UUID creditAccountId
    ) {
        return new CreateVoucherRequest(
            UUID.randomUUID(),
            type,
            LocalDate.of(2026, 7, 16),
            null,
            null,
            null,
            balancedLines(debitAccountId, creditAccountId)
        );
    }

    private List<VoucherLineRequest> balancedLines(UUID debitAccountId, UUID creditAccountId) {
        return List.of(
            new VoucherLineRequest(
                debitAccountId,
                null,
                new BigDecimal("100.0000"),
                BigDecimal.ZERO,
                null,
                null
            ),
            creditLine(creditAccountId, "100.0000")
        );
    }

    private VoucherLineRequest creditLine(UUID accountId, String amount) {
        return new VoucherLineRequest(
            accountId,
            null,
            BigDecimal.ZERO,
            new BigDecimal(amount),
            null,
            null
        );
    }

    private Voucher voucher(
        UUID companyId,
        String voucherNumber,
        BigDecimal totalDebit,
        BigDecimal totalCredit
    ) {
        return Voucher.create(
            companyId,
            voucherNumber,
            VoucherType.OFFSET,
            LocalDate.of(2026, 7, 16),
            null,
            null,
            "TRY",
            new BigDecimal("1.00000000"),
            UUID.randomUUID(),
            totalDebit,
            totalCredit
        );
    }

    private List<VoucherLine> voucherLines(
        UUID companyId,
        UUID voucherId,
        UUID debitAccountId,
        UUID creditAccountId,
        String debit,
        String credit
    ) {
        return List.of(
            VoucherLine.create(
                companyId,
                voucherId,
                1,
                debitAccountId,
                "100.01",
                "Main Cash",
                null,
                new BigDecimal(debit),
                BigDecimal.ZERO,
                null,
                null
            ),
            VoucherLine.create(
                companyId,
                voucherId,
                2,
                creditAccountId,
                "320.01.000001",
                "Supplier",
                null,
                BigDecimal.ZERO,
                new BigDecimal(credit),
                null,
                null
            )
        );
    }

    private List<PostingAccountReference> postingReferences(UUID debitAccountId, UUID creditAccountId) {
        return List.of(
            new PostingAccountReference(debitAccountId, "100.01", "Main Cash"),
            new PostingAccountReference(creditAccountId, "320.01.000001", "Supplier")
        );
    }
}
