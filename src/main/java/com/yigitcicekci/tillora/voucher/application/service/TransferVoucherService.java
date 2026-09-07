package com.yigitcicekci.tillora.voucher.application.service;

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
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
import com.yigitcicekci.tillora.voucher.domain.entity.VoucherLine;
import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferSourceAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferTargetAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherLineRepository;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherRepository;
import com.yigitcicekci.tillora.voucher.infrastructure.persistence.VoucherIdempotencyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TransferVoucherService {

    private static final BigDecimal LOCAL_EXCHANGE_RATE = new BigDecimal("1.00000000");
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.0000");

    private final VoucherRepository voucherRepository;
    private final VoucherLineRepository voucherLineRepository;
    private final VoucherNumberGenerator voucherNumberGenerator;
    private final VoucherIdempotencyRepository voucherIdempotencyRepository;
    private final FinancialPostingAccountService financialPostingAccountService;
    private final CurrentAccountService currentAccountService;
    private final ChartOfAccountService chartOfAccountService;
    private final CompanyService companyService;
    private final AuditLogService auditLogService;

    public TransferVoucherService(
        VoucherRepository voucherRepository,
        VoucherLineRepository voucherLineRepository,
        VoucherNumberGenerator voucherNumberGenerator,
        VoucherIdempotencyRepository voucherIdempotencyRepository,
        FinancialPostingAccountService financialPostingAccountService,
        CurrentAccountService currentAccountService,
        ChartOfAccountService chartOfAccountService,
        CompanyService companyService,
        AuditLogService auditLogService
    ) {
        this.voucherRepository = voucherRepository;
        this.voucherLineRepository = voucherLineRepository;
        this.voucherNumberGenerator = voucherNumberGenerator;
        this.voucherIdempotencyRepository = voucherIdempotencyRepository;
        this.financialPostingAccountService = financialPostingAccountService;
        this.currentAccountService = currentAccountService;
        this.chartOfAccountService = chartOfAccountService;
        this.companyService = companyService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public VoucherResponse create(UUID companyId, UUID actorUserId, CreateTransferVoucherRequest request) {
        NormalizedTransfer normalized = normalize(request);
        voucherIdempotencyRepository.acquire(companyId, normalized.idempotencyKey());
        Voucher existing = voucherRepository.findByCompanyIdAndIdempotencyKey(
            companyId, normalized.idempotencyKey()
        ).orElse(null);
        if (existing != null) {
            return existing(existing, companyId, normalized.requestFingerprint());
        }
        CompanyFinancialContext context = companyService.financialContext(companyId);
        LocalDate voucherDate = normalized.requestedDate() == null
            ? LocalDate.now(ZoneId.of(context.timezone()))
            : normalized.requestedDate();
        validateDate(voucherDate);
        TransferAccountReference source = sourceAccount(
            companyId, normalized.sourceAccountType(), normalized.sourceAccountId()
        );
        TransferAccountReference target = targetAccount(
            companyId, normalized.targetAccountType(), normalized.targetAccountId()
        );
        validateAccounts(context, source, target);
        Map<UUID, PostingAccountReference> postingAccounts = resolvePostingAccounts(
            companyId, source.chartOfAccountId(), target.chartOfAccountId()
        );
        String voucherNumber = voucherNumberGenerator.next(
            companyId, VoucherType.TRANSFER, voucherDate.getYear()
        );
        Voucher voucher = voucherRepository.saveAndFlush(Voucher.createGuided(
            companyId,
            voucherNumber,
            VoucherType.TRANSFER,
            voucherDate,
            normalized.movementNote(),
            normalized.documentNumber(),
            context.currency(),
            LOCAL_EXCHANGE_RATE,
            actorUserId,
            normalized.amount(),
            normalized.amount(),
            normalized.idempotencyKey(),
            normalized.requestFingerprint()
        ));
        PostingAccountReference targetPosting = postingAccounts.get(target.chartOfAccountId());
        PostingAccountReference sourcePosting = postingAccounts.get(source.chartOfAccountId());
        List<VoucherLine> lines = voucherLineRepository.saveAllAndFlush(List.of(
            VoucherLine.create(
                companyId,
                voucher.id(),
                1,
                targetPosting.id(),
                target.currentAccountId(),
                targetPosting.code(),
                targetPosting.name(),
                normalized.movementNote(),
                normalized.amount(),
                ZERO_AMOUNT,
                null,
                null
            ),
            VoucherLine.create(
                companyId,
                voucher.id(),
                2,
                sourcePosting.id(),
                source.currentAccountId(),
                sourcePosting.code(),
                sourcePosting.name(),
                normalized.movementNote(),
                ZERO_AMOUNT,
                normalized.amount(),
                null,
                null
            )
        ));
        auditLogService.record(
            companyId, actorUserId, AuditAction.VOUCHER_TRANSFER_CREATE, "VOUCHER", voucher.id()
        );
        return VoucherResponse.from(voucher, lines);
    }

    private VoucherResponse existing(Voucher voucher, UUID companyId, String requestFingerprint) {
        if (voucher.voucherType() != VoucherType.TRANSFER
            || !requestFingerprint.equals(voucher.requestFingerprint())) {
            throw new BusinessException(
                "IDEMPOTENCY_KEY_REUSED",
                "Idempotency key was already used for another voucher request.",
                HttpStatus.CONFLICT
            );
        }
        List<VoucherLine> lines = voucherLineRepository
            .findByVoucherIdAndCompanyIdOrderByLineNumberAsc(voucher.id(), companyId);
        return VoucherResponse.from(voucher, lines);
    }

    private NormalizedTransfer normalize(CreateTransferVoucherRequest request) {
        if (request == null
            || request.idempotencyKey() == null
            || request.sourceAccountType() == null
            || request.sourceAccountId() == null
            || request.targetAccountType() == null
            || request.targetAccountId() == null) {
            throw new BusinessException(
                "TRANSFER_REQUEST_INVALID", "Transfer request is incomplete.", HttpStatus.BAD_REQUEST
            );
        }
        if (request.sourceAccountType().name().equals(request.targetAccountType().name())
            && request.sourceAccountId().equals(request.targetAccountId())) {
            throw new BusinessException(
                "TRANSFER_ACCOUNTS_MUST_DIFFER",
                "Transfer source and target accounts must differ.",
                HttpStatus.BAD_REQUEST
            );
        }
        BigDecimal amount = normalizeAmount(request.amount());
        String movementNote = normalizeOptionalText(request.movementNote(), 500);
        String documentNumber = normalizeOptionalText(request.documentNumber(), 80);
        return new NormalizedTransfer(
            request.idempotencyKey(),
            request.sourceAccountType(),
            request.sourceAccountId(),
            request.targetAccountType(),
            request.targetAccountId(),
            amount,
            request.voucherDate(),
            movementNote,
            documentNumber,
            GuidedVoucherRequestFingerprint.transfer(
                request.sourceAccountType(),
                request.sourceAccountId(),
                request.targetAccountType(),
                request.targetAccountId(),
                amount,
                request.voucherDate(),
                movementNote,
                documentNumber
            )
        );
    }

    private TransferAccountReference sourceAccount(
        UUID companyId, TransferSourceAccountType type, UUID accountId
    ) {
        return switch (type) {
            case CASH -> financialAccount(
                financialPostingAccountService.findActiveCashPostingAccount(companyId, accountId)
            );
            case BANK -> financialAccount(
                financialPostingAccountService.findActiveBankPostingAccount(companyId, accountId)
            );
            case CUSTOMER -> receivableAccount(companyId, accountId);
            case SUPPLIER -> payableAccount(companyId, accountId);
        };
    }

    private TransferAccountReference targetAccount(
        UUID companyId, TransferTargetAccountType type, UUID accountId
    ) {
        return switch (type) {
            case CASH -> financialAccount(
                financialPostingAccountService.findActiveCashPostingAccount(companyId, accountId)
            );
            case BANK -> financialAccount(
                financialPostingAccountService.findActiveBankPostingAccount(companyId, accountId)
            );
            case SUPPLIER -> payableAccount(companyId, accountId);
        };
    }

    private TransferAccountReference financialAccount(FinancialPostingAccountReference account) {
        return new TransferAccountReference(account.chartOfAccountId(), null, account.currency());
    }

    private TransferAccountReference receivableAccount(UUID companyId, UUID currentAccountId) {
        CurrentAccountPostingReference account = currentAccountService.findActiveTransferReceivablePostingAccount(
            companyId, currentAccountId
        );
        return currentAccount(account);
    }

    private TransferAccountReference payableAccount(UUID companyId, UUID currentAccountId) {
        CurrentAccountPostingReference account = currentAccountService.findActiveTransferPayablePostingAccount(
            companyId, currentAccountId
        );
        return currentAccount(account);
    }

    private TransferAccountReference currentAccount(CurrentAccountPostingReference account) {
        return new TransferAccountReference(account.chartOfAccountId(), account.currentAccountId(), null);
    }

    private void validateAccounts(
        CompanyFinancialContext context,
        TransferAccountReference source,
        TransferAccountReference target
    ) {
        if (source.chartOfAccountId().equals(target.chartOfAccountId())) {
            throw new BusinessException(
                "TRANSFER_ACCOUNTS_MUST_DIFFER",
                "Transfer source and target accounts must differ.",
                HttpStatus.BAD_REQUEST
            );
        }
        if ((source.currency() != null && !context.currency().equals(source.currency()))
            || (target.currency() != null && !context.currency().equals(target.currency()))) {
            throw new BusinessException(
                "TRANSFER_ACCOUNT_CURRENCY_MISMATCH",
                "Transfer accounts must use the company currency.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private Map<UUID, PostingAccountReference> resolvePostingAccounts(
        UUID companyId, UUID sourceChartAccountId, UUID targetChartAccountId
    ) {
        List<PostingAccountReference> references = chartOfAccountService.findActivePostingAccounts(
            companyId, List.of(sourceChartAccountId, targetChartAccountId)
        );
        Map<UUID, PostingAccountReference> accounts = new LinkedHashMap<>();
        for (PostingAccountReference reference : references) {
            accounts.put(reference.id(), reference);
        }
        if (accounts.size() != 2
            || !accounts.containsKey(sourceChartAccountId)
            || !accounts.containsKey(targetChartAccountId)) {
            throw new BusinessException(
                "TRANSFER_POSTING_ACCOUNT_INVALID",
                "Transfer accounts must be active posting accounts.",
                HttpStatus.BAD_REQUEST
            );
        }
        return accounts;
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessException(
                "TRANSFER_AMOUNT_INVALID", "Transfer amount must be greater than zero.", HttpStatus.BAD_REQUEST
            );
        }
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                "TRANSFER_AMOUNT_INVALID",
                "Transfer amount supports at most 4 decimal places.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateDate(LocalDate voucherDate) {
        if (voucherDate.getYear() < 2000 || voucherDate.getYear() > 9999) {
            throw new BusinessException(
                "VOUCHER_DATE_INVALID", "Voucher date is outside the supported range.", HttpStatus.BAD_REQUEST
            );
        }
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(
                "VOUCHER_TEXT_TOO_LONG", "Voucher text exceeds the supported length.", HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private record NormalizedTransfer(
        UUID idempotencyKey,
        TransferSourceAccountType sourceAccountType,
        UUID sourceAccountId,
        TransferTargetAccountType targetAccountType,
        UUID targetAccountId,
        BigDecimal amount,
        LocalDate requestedDate,
        String movementNote,
        String documentNumber,
        String requestFingerprint
    ) {
    }

    private record TransferAccountReference(
        UUID chartOfAccountId,
        UUID currentAccountId,
        String currency
    ) {
    }
}
