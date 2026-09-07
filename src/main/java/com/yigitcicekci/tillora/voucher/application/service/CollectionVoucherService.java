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
public class CollectionVoucherService {

    private static final BigDecimal LOCAL_EXCHANGE_RATE = new BigDecimal("1.00000000");
    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.0000");

    private final VoucherRepository voucherRepository;
    private final VoucherLineRepository voucherLineRepository;
    private final VoucherNumberGenerator voucherNumberGenerator;
    private final VoucherIdempotencyRepository voucherIdempotencyRepository;
    private final CurrentAccountService currentAccountService;
    private final FinancialPostingAccountService financialPostingAccountService;
    private final ChartOfAccountService chartOfAccountService;
    private final CompanyService companyService;
    private final AuditLogService auditLogService;

    public CollectionVoucherService(
        VoucherRepository voucherRepository,
        VoucherLineRepository voucherLineRepository,
        VoucherNumberGenerator voucherNumberGenerator,
        VoucherIdempotencyRepository voucherIdempotencyRepository,
        CurrentAccountService currentAccountService,
        FinancialPostingAccountService financialPostingAccountService,
        ChartOfAccountService chartOfAccountService,
        CompanyService companyService,
        AuditLogService auditLogService
    ) {
        this.voucherRepository = voucherRepository;
        this.voucherLineRepository = voucherLineRepository;
        this.voucherNumberGenerator = voucherNumberGenerator;
        this.voucherIdempotencyRepository = voucherIdempotencyRepository;
        this.currentAccountService = currentAccountService;
        this.financialPostingAccountService = financialPostingAccountService;
        this.chartOfAccountService = chartOfAccountService;
        this.companyService = companyService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public VoucherResponse create(
        UUID companyId,
        UUID actorUserId,
        CreateCollectionVoucherRequest request
    ) {
        NormalizedCollection normalized = normalize(request);
        voucherIdempotencyRepository.acquire(companyId, normalized.idempotencyKey());
        Voucher existing = voucherRepository.findByCompanyIdAndIdempotencyKey(
            companyId,
            normalized.idempotencyKey()
        ).orElse(null);
        if (existing != null) {
            return existing(existing, companyId, normalized.requestFingerprint());
        }
        CompanyFinancialContext context = companyService.financialContext(companyId);
        LocalDate voucherDate = normalized.requestedDate() == null
            ? LocalDate.now(ZoneId.of(context.timezone()))
            : normalized.requestedDate();
        validateDate(voucherDate);
        CurrentAccountPostingReference currentAccount =
            currentAccountService.findActiveReceivablePostingAccount(companyId, normalized.currentAccountId());
        FinancialPostingAccountReference settlementAccount = settlementAccount(
            companyId,
            normalized.settlementAccountType(),
            normalized.settlementAccountId()
        );
        if (!context.currency().equals(settlementAccount.currency())) {
            throw new BusinessException(
                "COLLECTION_ACCOUNT_CURRENCY_MISMATCH",
                "Collection account currency must match the company currency.",
                HttpStatus.BAD_REQUEST
            );
        }
        Map<UUID, PostingAccountReference> postingAccounts = resolvePostingAccounts(
            companyId,
            settlementAccount.chartOfAccountId(),
            currentAccount.chartOfAccountId()
        );
        String voucherNumber = voucherNumberGenerator.next(companyId, VoucherType.COLLECTION, voucherDate.getYear());
        Voucher voucher = voucherRepository.saveAndFlush(Voucher.createGuided(
            companyId,
            voucherNumber,
            VoucherType.COLLECTION,
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
        PostingAccountReference settlementPosting = postingAccounts.get(settlementAccount.chartOfAccountId());
        PostingAccountReference currentPosting = postingAccounts.get(currentAccount.chartOfAccountId());
        List<VoucherLine> lines = voucherLineRepository.saveAllAndFlush(List.of(
            VoucherLine.create(
                companyId,
                voucher.id(),
                1,
                settlementPosting.id(),
                settlementPosting.code(),
                settlementPosting.name(),
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
                currentPosting.id(),
                currentAccount.currentAccountId(),
                currentPosting.code(),
                currentPosting.name(),
                normalized.movementNote(),
                ZERO_AMOUNT,
                normalized.amount(),
                null,
                null
            )
        ));
        auditLogService.record(companyId, actorUserId, AuditAction.VOUCHER_CREATE, "VOUCHER", voucher.id());
        return VoucherResponse.from(voucher, lines);
    }

    private VoucherResponse existing(Voucher voucher, UUID companyId, String requestFingerprint) {
        if (voucher.voucherType() != VoucherType.COLLECTION
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

    private NormalizedCollection normalize(CreateCollectionVoucherRequest request) {
        if (request == null
            || request.idempotencyKey() == null
            || request.currentAccountId() == null
            || request.settlementAccountType() == null
            || request.settlementAccountId() == null) {
            throw new BusinessException(
                "COLLECTION_REQUEST_INVALID",
                "Collection request is incomplete.",
                HttpStatus.BAD_REQUEST
            );
        }
        BigDecimal amount = normalizeAmount(request.amount());
        String movementNote = normalizeOptionalText(request.movementNote(), 500);
        String documentNumber = normalizeOptionalText(request.documentNumber(), 80);
        String fingerprint = GuidedVoucherRequestFingerprint.create(
            request.currentAccountId(),
            request.settlementAccountType(),
            request.settlementAccountId(),
            amount,
            request.voucherDate(),
            movementNote,
            documentNumber
        );
        return new NormalizedCollection(
            request.idempotencyKey(),
            request.currentAccountId(),
            request.settlementAccountType(),
            request.settlementAccountId(),
            amount,
            request.voucherDate(),
            movementNote,
            documentNumber,
            fingerprint
        );
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessException(
                "COLLECTION_AMOUNT_INVALID",
                "Collection amount must be greater than zero.",
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                "COLLECTION_AMOUNT_INVALID",
                "Collection amount supports at most 4 decimal places.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateDate(LocalDate voucherDate) {
        if (voucherDate.getYear() < 2000 || voucherDate.getYear() > 9999) {
            throw new BusinessException(
                "VOUCHER_DATE_INVALID",
                "Voucher date is outside the supported range.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private FinancialPostingAccountReference settlementAccount(
        UUID companyId,
        SettlementAccountType type,
        UUID accountId
    ) {
        return switch (type) {
            case CASH -> financialPostingAccountService.findActiveCashPostingAccount(companyId, accountId);
            case BANK -> financialPostingAccountService.findActiveBankPostingAccount(companyId, accountId);
        };
    }

    private Map<UUID, PostingAccountReference> resolvePostingAccounts(
        UUID companyId,
        UUID settlementChartAccountId,
        UUID currentChartAccountId
    ) {
        List<UUID> accountIds = List.of(settlementChartAccountId, currentChartAccountId);
        List<PostingAccountReference> references =
            chartOfAccountService.findActivePostingAccounts(companyId, accountIds);
        Map<UUID, PostingAccountReference> accounts = new LinkedHashMap<>();
        for (PostingAccountReference reference : references) {
            accounts.put(reference.id(), reference);
        }
        if (accounts.size() != 2
            || !accounts.containsKey(settlementChartAccountId)
            || !accounts.containsKey(currentChartAccountId)) {
            throw new BusinessException(
                "COLLECTION_POSTING_ACCOUNT_INVALID",
                "Collection accounts must be active posting accounts.",
                HttpStatus.BAD_REQUEST
            );
        }
        return accounts;
    }

    private String normalizeOptionalText(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new BusinessException(
                "VOUCHER_TEXT_TOO_LONG",
                "Voucher text exceeds the supported length.",
                HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private record NormalizedCollection(
        UUID idempotencyKey,
        UUID currentAccountId,
        SettlementAccountType settlementAccountType,
        UUID settlementAccountId,
        BigDecimal amount,
        LocalDate requestedDate,
        String movementNote,
        String documentNumber,
        String requestFingerprint
    ) {
    }
}
