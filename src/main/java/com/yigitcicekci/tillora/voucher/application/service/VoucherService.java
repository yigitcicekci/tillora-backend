package com.yigitcicekci.tillora.voucher.application.service;

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
import com.yigitcicekci.tillora.voucher.api.response.VoucherSummaryResponse;
import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
import com.yigitcicekci.tillora.voucher.domain.entity.VoucherLine;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherLineRepository;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherRepository;
import com.yigitcicekci.tillora.voucher.infrastructure.persistence.VoucherIdempotencyRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Currency;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VoucherService {

    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.0000");
    private static final BigDecimal LOCAL_EXCHANGE_RATE = new BigDecimal("1.00000000");

    private final VoucherRepository voucherRepository;
    private final VoucherLineRepository voucherLineRepository;
    private final VoucherNumberGenerator voucherNumberGenerator;
    private final VoucherIdempotencyRepository voucherIdempotencyRepository;
    private final FinancialPostingAccountService financialPostingAccountService;
    private final ChartOfAccountService chartOfAccountService;
    private final CompanyService companyService;
    private final AuditLogService auditLogService;

    public VoucherService(
        VoucherRepository voucherRepository,
        VoucherLineRepository voucherLineRepository,
        VoucherNumberGenerator voucherNumberGenerator,
        VoucherIdempotencyRepository voucherIdempotencyRepository,
        FinancialPostingAccountService financialPostingAccountService,
        ChartOfAccountService chartOfAccountService,
        CompanyService companyService,
        AuditLogService auditLogService
    ) {
        this.voucherRepository = voucherRepository;
        this.voucherLineRepository = voucherLineRepository;
        this.voucherNumberGenerator = voucherNumberGenerator;
        this.voucherIdempotencyRepository = voucherIdempotencyRepository;
        this.financialPostingAccountService = financialPostingAccountService;
        this.chartOfAccountService = chartOfAccountService;
        this.companyService = companyService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public VoucherResponse create(UUID companyId, UUID actorUserId, CreateVoucherRequest request) {
        if (request == null || request.idempotencyKey() == null) {
            throw new BusinessException(
                "VOUCHER_IDEMPOTENCY_KEY_REQUIRED",
                "Idempotency key is required.",
                HttpStatus.BAD_REQUEST
            );
        }
        validateManualType(request.voucherType());
        CompanyFinancialContext context = companyService.financialContext(companyId);
        PreparedHeader header = prepareHeader(
            request.voucherDate(),
            request.movementNote(),
            request.documentNumber(),
            request.currency(),
            context
        );
        PreparedVoucher prepared = prepareLines(request.lines());
        String requestFingerprint = requestFingerprint(
            request.voucherType(),
            request.voucherDate(),
            header,
            prepared
        );
        voucherIdempotencyRepository.acquire(companyId, request.idempotencyKey());
        Voucher existing = voucherRepository.findByCompanyIdAndIdempotencyKey(
            companyId,
            request.idempotencyKey()
        ).orElse(null);
        if (existing != null) {
            return existing(existing, companyId, requestFingerprint);
        }
        Map<UUID, PostingAccountReference> accounts = resolvePostingAccounts(companyId, prepared.lines());
        String voucherNumber = voucherNumberGenerator.next(companyId, request.voucherType(), header.voucherDate().getYear());
        Voucher voucher = voucherRepository.saveAndFlush(Voucher.createManual(
            companyId,
            voucherNumber,
            request.voucherType(),
            header.voucherDate(),
            header.movementNote(),
            header.documentNumber(),
            header.currency(),
            LOCAL_EXCHANGE_RATE,
            actorUserId,
            prepared.totalDebit(),
            prepared.totalCredit(),
            request.idempotencyKey(),
            requestFingerprint
        ));
        List<VoucherLine> lines = saveLines(companyId, voucher.id(), prepared.lines(), accounts);
        auditLogService.record(companyId, actorUserId, AuditAction.VOUCHER_CREATE, "VOUCHER", voucher.id());
        return VoucherResponse.from(voucher, lines);
    }

    private VoucherResponse existing(Voucher voucher, UUID companyId, String requestFingerprint) {
        if (voucher.voucherType() != VoucherType.OFFSET
            || voucher.sourceType() != null
            || !requestFingerprint.equals(voucher.requestFingerprint())) {
            throw new BusinessException(
                "IDEMPOTENCY_KEY_REUSED",
                "Idempotency key was already used for another voucher request.",
                HttpStatus.CONFLICT
            );
        }
        return VoucherResponse.from(voucher, findLines(companyId, voucher.id()));
    }

    @Transactional
    public VoucherResponse update(
        UUID companyId,
        UUID actorUserId,
        UUID voucherId,
        UpdateVoucherRequest request
    ) {
        Voucher voucher = findForUpdate(companyId, voucherId);
        if (voucher.status() != VoucherStatus.DRAFT) {
            throw new BusinessException(
                "VOUCHER_NOT_DRAFT",
                "Only draft vouchers can be updated.",
                HttpStatus.CONFLICT
            );
        }
        if (voucher.voucherType() != VoucherType.OFFSET) {
            throw new BusinessException(
                "GUIDED_VOUCHER_CANNOT_BE_MANUALLY_UPDATED",
                "Guided vouchers cannot be replaced with manual accounting lines.",
                HttpStatus.CONFLICT
            );
        }
        CompanyFinancialContext context = companyService.financialContext(companyId);
        PreparedHeader header = prepareHeader(
            request.voucherDate(),
            request.movementNote(),
            request.documentNumber(),
            request.currency(),
            context
        );
        if (header.voucherDate().getYear() != voucher.voucherDate().getYear()) {
            throw new BusinessException(
                "VOUCHER_YEAR_CANNOT_CHANGE",
                "Voucher date cannot be moved to another calendar year.",
                HttpStatus.CONFLICT
            );
        }
        PreparedVoucher prepared = prepareLines(request.lines());
        Map<UUID, PostingAccountReference> accounts = resolvePostingAccounts(companyId, prepared.lines());
        voucher.updateDraft(
            header.voucherDate(),
            header.movementNote(),
            header.documentNumber(),
            header.currency(),
            prepared.totalDebit(),
            prepared.totalCredit()
        );
        voucherRepository.saveAndFlush(voucher);
        voucherLineRepository.deleteAllByVoucherIdAndCompanyId(voucher.id(), companyId);
        voucherLineRepository.flush();
        List<VoucherLine> lines = saveLines(companyId, voucher.id(), prepared.lines(), accounts);
        auditLogService.record(companyId, actorUserId, AuditAction.VOUCHER_UPDATE, "VOUCHER", voucher.id());
        return VoucherResponse.from(voucher, lines);
    }

    @Transactional(readOnly = true)
    public Page<VoucherSummaryResponse> list(
        UUID companyId,
        VoucherType voucherType,
        VoucherStatus status,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException(
                "VOUCHER_DATE_RANGE_INVALID",
                "Voucher start date cannot be after end date.",
                HttpStatus.BAD_REQUEST
            );
        }
        return voucherRepository.findAllByFilters(
            companyId,
            voucherType,
            status,
            dateFrom,
            dateTo,
            pageable
        ).map(VoucherSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public VoucherResponse get(UUID companyId, UUID voucherId) {
        Voucher voucher = find(companyId, voucherId);
        return VoucherResponse.from(voucher, findLines(companyId, voucher.id()));
    }

    @Transactional
    public VoucherResponse approve(UUID companyId, UUID actorUserId, UUID voucherId) {
        Voucher voucher = findForUpdate(companyId, voucherId);
        List<VoucherLine> lines = findLines(companyId, voucher.id());
        if (voucher.status() == VoucherStatus.APPROVED) {
            return VoucherResponse.from(voucher, lines);
        }
        if (voucher.status() == VoucherStatus.CANCELLED) {
            throw new BusinessException(
                "VOUCHER_CANCELLED",
                "Cancelled vouchers cannot be approved.",
                HttpStatus.CONFLICT
            );
        }
        validatePostingAccounts(companyId, lines.stream().map(VoucherLine::chartOfAccountId).toList());
        Totals totals = calculateTotals(lines);
        if (totals.totalDebit().compareTo(voucher.totalDebit()) != 0
            || totals.totalCredit().compareTo(voucher.totalCredit()) != 0) {
            throw new BusinessException(
                "VOUCHER_TOTALS_INCONSISTENT",
                "Voucher totals do not match its lines.",
                HttpStatus.CONFLICT
            );
        }
        if (totals.totalDebit().signum() <= 0 || totals.totalDebit().compareTo(totals.totalCredit()) != 0) {
            throw new BusinessException(
                "VOUCHER_NOT_BALANCED",
                "Voucher debit and credit totals must be equal and greater than zero.",
                HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
        validateCashBalance(companyId, voucher, lines, false);
        voucher.approve(actorUserId);
        voucherRepository.saveAndFlush(voucher);
        auditLogService.record(companyId, actorUserId, AuditAction.VOUCHER_APPROVE, "VOUCHER", voucher.id());
        return VoucherResponse.from(voucher, lines);
    }

    @Transactional
    public VoucherResponse cancel(UUID companyId, UUID actorUserId, UUID voucherId, String reason) {
        String normalizedReason = normalizeRequiredText(
            reason,
            500,
            "VOUCHER_CANCELLATION_REASON_REQUIRED",
            "Voucher cancellation reason is required."
        );
        Voucher voucher = findForUpdate(companyId, voucherId);
        List<VoucherLine> lines = findLines(companyId, voucher.id());
        if (voucher.status() == VoucherStatus.CANCELLED) {
            return VoucherResponse.from(voucher, lines);
        }
        if (voucher.status() != VoucherStatus.APPROVED) {
            throw new BusinessException(
                "VOUCHER_NOT_APPROVED",
                "Only approved vouchers can be cancelled.",
                HttpStatus.CONFLICT
            );
        }
        if (voucher.sourceType() != null) {
            throw new BusinessException(
                "SOURCE_VOUCHER_CANNOT_BE_CANCELLED_DIRECTLY",
                "Source-generated vouchers must be cancelled through their source document.",
                HttpStatus.CONFLICT
            );
        }
        validateCashBalance(companyId, voucher, lines, true);
        voucher.cancel(actorUserId, normalizedReason);
        voucherRepository.saveAndFlush(voucher);
        auditLogService.record(companyId, actorUserId, AuditAction.VOUCHER_CANCEL, "VOUCHER", voucher.id());
        return VoucherResponse.from(voucher, lines);
    }

    private void validateCashBalance(
        UUID companyId,
        Voucher voucher,
        List<VoucherLine> lines,
        boolean cancellation
    ) {
        Set<UUID> chartOfAccountIds = lines.stream()
            .map(VoucherLine::chartOfAccountId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<UUID> cashAccountChartIds = financialPostingAccountService.lockCashAccountsForBalance(
            companyId,
            chartOfAccountIds
        );
        if (cashAccountChartIds.isEmpty()) {
            return;
        }
        Map<UUID, BigDecimal> movementByAccount = new LinkedHashMap<>();
        for (VoucherLine line : lines) {
            if (!cashAccountChartIds.contains(line.chartOfAccountId())) {
                continue;
            }
            BigDecimal movement = line.debit()
                .subtract(line.credit())
                .multiply(voucher.exchangeRate());
            if (cancellation) {
                movement = movement.negate();
            }
            movementByAccount.merge(line.chartOfAccountId(), movement, BigDecimal::add);
        }
        Map<UUID, BigDecimal> balanceByAccount = new LinkedHashMap<>();
        List<UUID> cashAccountChartIdList = cashAccountChartIds.stream().toList();
        for (VoucherLineRepository.ApprovedBalance balance : voucherLineRepository.findBalances(
            companyId,
            cashAccountChartIdList,
            VoucherStatus.APPROVED
        )) {
            balanceByAccount.put(balance.getChartOfAccountId(), balance.getBalance());
        }
        for (UUID cashAccountChartId : cashAccountChartIds) {
            BigDecimal projectedBalance = balanceByAccount.getOrDefault(cashAccountChartId, ZERO_AMOUNT)
                .add(movementByAccount.getOrDefault(cashAccountChartId, ZERO_AMOUNT));
            if (projectedBalance.signum() < 0) {
                throw new BusinessException(
                    "CASH_ACCOUNT_BALANCE_INSUFFICIENT",
                    "Cash account balance is insufficient for this transaction.",
                    HttpStatus.UNPROCESSABLE_CONTENT
                );
            }
        }
    }

    private void validateManualType(VoucherType voucherType) {
        if (voucherType == null) {
            throw new BusinessException(
                "VOUCHER_TYPE_REQUIRED",
                "Voucher type is required.",
                HttpStatus.BAD_REQUEST
            );
        }
        if (voucherType != VoucherType.OFFSET) {
            throw new BusinessException(
                "MANUAL_VOUCHER_TYPE_NOT_SUPPORTED",
                "Collection and payment vouchers must be created through guided financial flows.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private PreparedHeader prepareHeader(
        LocalDate requestedDate,
        String movementNote,
        String documentNumber,
        String requestedCurrency,
        CompanyFinancialContext context
    ) {
        LocalDate voucherDate = requestedDate == null
            ? LocalDate.now(ZoneId.of(context.timezone()))
            : requestedDate;
        if (voucherDate.getYear() < 2000 || voucherDate.getYear() > 9999) {
            throw new BusinessException(
                "VOUCHER_DATE_INVALID",
                "Voucher date is outside the supported range.",
                HttpStatus.BAD_REQUEST
            );
        }
        return new PreparedHeader(
            voucherDate,
            normalizeOptionalText(movementNote, 500),
            normalizeOptionalText(documentNumber, 80),
            resolveCurrency(context.currency(), requestedCurrency)
        );
    }

    private PreparedVoucher prepareLines(List<VoucherLineRequest> requests) {
        if (requests == null || requests.size() < 2 || requests.size() > 100) {
            throw new BusinessException(
                "VOUCHER_LINE_COUNT_INVALID",
                "Voucher must contain between 2 and 100 lines.",
                HttpStatus.BAD_REQUEST
            );
        }
        List<PreparedLine> lines = java.util.stream.IntStream.range(0, requests.size())
            .mapToObj(index -> prepareLine(index + 1, requests.get(index)))
            .toList();
        Totals totals = calculatePreparedTotals(lines);
        return new PreparedVoucher(lines, totals.totalDebit(), totals.totalCredit());
    }

    private PreparedLine prepareLine(int lineNumber, VoucherLineRequest request) {
        if (request == null || request.chartOfAccountId() == null) {
            throw new BusinessException(
                "VOUCHER_POSTING_ACCOUNT_INVALID",
                "Every voucher line must reference an active posting account.",
                HttpStatus.BAD_REQUEST
            );
        }
        BigDecimal debit = normalizeAmount(request.debit());
        BigDecimal credit = normalizeAmount(request.credit());
        if ((debit.signum() > 0) == (credit.signum() > 0)) {
            throw new BusinessException(
                "VOUCHER_LINE_SIDE_INVALID",
                "Every voucher line must have either a debit or a credit amount.",
                HttpStatus.BAD_REQUEST
            );
        }
        BigDecimal quantity = normalizeQuantity(request.quantity());
        return new PreparedLine(
            lineNumber,
            request.chartOfAccountId(),
            normalizeOptionalText(request.movementNote(), 500),
            debit,
            credit,
            quantity,
            request.dueDate()
        );
    }

    private Map<UUID, PostingAccountReference> resolvePostingAccounts(
        UUID companyId,
        List<PreparedLine> lines
    ) {
        Set<UUID> accountIds = lines.stream()
            .map(PreparedLine::chartOfAccountId)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<PostingAccountReference> references =
            chartOfAccountService.findActivePostingAccounts(companyId, accountIds);
        Map<UUID, PostingAccountReference> accounts = new LinkedHashMap<>();
        for (PostingAccountReference reference : references) {
            accounts.put(reference.id(), reference);
        }
        if (!accounts.keySet().equals(accountIds)) {
            throw new BusinessException(
                "VOUCHER_POSTING_ACCOUNT_INVALID",
                "Every voucher line must reference an active posting account in the same company.",
                HttpStatus.BAD_REQUEST
            );
        }
        return accounts;
    }

    private void validatePostingAccounts(UUID companyId, Collection<UUID> accountIds) {
        Set<UUID> distinctIds = new LinkedHashSet<>(accountIds);
        List<PostingAccountReference> references =
            chartOfAccountService.findActivePostingAccounts(companyId, distinctIds);
        Set<UUID> resolvedIds = references.stream()
            .map(PostingAccountReference::id)
            .collect(java.util.stream.Collectors.toSet());
        if (!resolvedIds.equals(distinctIds)) {
            throw new BusinessException(
                "VOUCHER_POSTING_ACCOUNT_INVALID",
                "Voucher contains an inactive, non-posting, or foreign account.",
                HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private List<VoucherLine> saveLines(
        UUID companyId,
        UUID voucherId,
        List<PreparedLine> preparedLines,
        Map<UUID, PostingAccountReference> accounts
    ) {
        List<VoucherLine> lines = preparedLines.stream()
            .map(line -> {
                PostingAccountReference account = accounts.get(line.chartOfAccountId());
                return VoucherLine.create(
                    companyId,
                    voucherId,
                    line.lineNumber(),
                    account.id(),
                    account.code(),
                    account.name(),
                    line.movementNote(),
                    line.debit(),
                    line.credit(),
                    line.quantity(),
                    line.dueDate()
                );
            })
            .toList();
        return voucherLineRepository.saveAllAndFlush(lines);
    }

    private Totals calculatePreparedTotals(List<PreparedLine> lines) {
        BigDecimal totalDebit = ZERO_AMOUNT;
        BigDecimal totalCredit = ZERO_AMOUNT;
        for (PreparedLine line : lines) {
            totalDebit = totalDebit.add(line.debit());
            totalCredit = totalCredit.add(line.credit());
        }
        return new Totals(totalDebit, totalCredit);
    }

    private Totals calculateTotals(List<VoucherLine> lines) {
        if (lines.size() < 2 || lines.size() > 100) {
            throw new BusinessException(
                "VOUCHER_LINE_COUNT_INVALID",
                "Voucher must contain between 2 and 100 lines.",
                HttpStatus.CONFLICT
            );
        }
        BigDecimal totalDebit = ZERO_AMOUNT;
        BigDecimal totalCredit = ZERO_AMOUNT;
        for (VoucherLine line : lines) {
            totalDebit = totalDebit.add(line.debit());
            totalCredit = totalCredit.add(line.credit());
        }
        return new Totals(totalDebit, totalCredit);
    }

    private BigDecimal normalizeAmount(BigDecimal value) {
        if (value == null || value.signum() < 0) {
            throw new BusinessException(
                "VOUCHER_AMOUNT_INVALID",
                "Voucher amounts must be non-negative.",
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                "VOUCHER_AMOUNT_INVALID",
                "Voucher amounts support at most 4 decimal places.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private BigDecimal normalizeQuantity(BigDecimal value) {
        if (value == null) {
            return null;
        }
        if (value.signum() <= 0) {
            throw new BusinessException(
                "VOUCHER_QUANTITY_INVALID",
                "Voucher quantity must be greater than zero.",
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            return value.setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                "VOUCHER_QUANTITY_INVALID",
                "Voucher quantity supports at most 6 decimal places.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private String resolveCurrency(String companyCurrency, String requestedCurrency) {
        String normalizedCompanyCurrency = normalizeCurrency(companyCurrency);
        if (requestedCurrency == null) {
            return normalizedCompanyCurrency;
        }
        String normalizedRequestedCurrency = normalizeCurrency(requestedCurrency);
        if (!normalizedCompanyCurrency.equals(normalizedRequestedCurrency)) {
            throw new BusinessException(
                "VOUCHER_FOREIGN_CURRENCY_NOT_SUPPORTED",
                "Manual vouchers currently support only the company currency.",
                HttpStatus.BAD_REQUEST
            );
        }
        return normalizedRequestedCurrency;
    }

    private String normalizeCurrency(String value) {
        try {
            return Currency.getInstance(value.trim().toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new BusinessException(
                "INVALID_CURRENCY",
                "Currency code is invalid.",
                HttpStatus.BAD_REQUEST
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
                "VOUCHER_TEXT_TOO_LONG",
                "Voucher text exceeds the supported length.",
                HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private String normalizeRequiredText(String value, int maxLength, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message, HttpStatus.BAD_REQUEST);
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

    private String requestFingerprint(
        VoucherType voucherType,
        LocalDate requestedDate,
        PreparedHeader header,
        PreparedVoucher voucher
    ) {
        StringBuilder canonical = new StringBuilder();
        appendCanonical(canonical, voucherType);
        appendCanonical(canonical, requestedDate);
        appendCanonical(canonical, header.movementNote());
        appendCanonical(canonical, header.documentNumber());
        appendCanonical(canonical, header.currency());
        for (PreparedLine line : voucher.lines()) {
            appendCanonical(canonical, line.lineNumber());
            appendCanonical(canonical, line.chartOfAccountId());
            appendCanonical(canonical, line.movementNote());
            appendCanonical(canonical, line.debit().toPlainString());
            appendCanonical(canonical, line.credit().toPlainString());
            appendCanonical(canonical, line.quantity() == null ? null : line.quantity().toPlainString());
            appendCanonical(canonical, line.dueDate());
        }
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(canonical.toString().getBytes(StandardCharsets.UTF_8))
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private void appendCanonical(StringBuilder canonical, Object value) {
        String text = value == null ? "" : value.toString();
        canonical.append(text.length()).append(':').append(text);
    }

    private Voucher find(UUID companyId, UUID voucherId) {
        return voucherRepository.findByIdAndCompanyId(voucherId, companyId)
            .orElseThrow(() -> new BusinessException(
                "VOUCHER_NOT_FOUND",
                "Voucher not found.",
                HttpStatus.NOT_FOUND
            ));
    }

    private Voucher findForUpdate(UUID companyId, UUID voucherId) {
        return voucherRepository.findForUpdate(voucherId, companyId)
            .orElseThrow(() -> new BusinessException(
                "VOUCHER_NOT_FOUND",
                "Voucher not found.",
                HttpStatus.NOT_FOUND
            ));
    }

    private List<VoucherLine> findLines(UUID companyId, UUID voucherId) {
        return voucherLineRepository.findByVoucherIdAndCompanyIdOrderByLineNumberAsc(voucherId, companyId);
    }

    private record PreparedHeader(
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        String currency
    ) {
    }

    private record PreparedLine(
        int lineNumber,
        UUID chartOfAccountId,
        String movementNote,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal quantity,
        LocalDate dueDate
    ) {
    }

    private record PreparedVoucher(
        List<PreparedLine> lines,
        BigDecimal totalDebit,
        BigDecimal totalCredit
    ) {
    }

    private record Totals(BigDecimal totalDebit, BigDecimal totalCredit) {
    }
}
