package com.yigitcicekci.tillora.invoice.application.service;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.company.application.service.CompanyFinancialContext;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountMovementService;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountPostingReference;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
import com.yigitcicekci.tillora.invoice.api.request.CreateInvoiceSettlementRequest;
import com.yigitcicekci.tillora.invoice.api.request.CreatePurchaseInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.request.CreateSalesInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.request.InvoiceLineRequest;
import com.yigitcicekci.tillora.invoice.api.request.UpdateInvoiceRequest;
import com.yigitcicekci.tillora.invoice.api.response.InvoiceResponse;
import com.yigitcicekci.tillora.invoice.api.response.InvoiceSummaryResponse;
import com.yigitcicekci.tillora.invoice.domain.entity.Invoice;
import com.yigitcicekci.tillora.invoice.domain.entity.InvoiceLine;
import com.yigitcicekci.tillora.invoice.domain.entity.InvoiceSettlementAllocation;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import com.yigitcicekci.tillora.invoice.domain.repository.InvoiceLineRepository;
import com.yigitcicekci.tillora.invoice.domain.repository.InvoiceRepository;
import com.yigitcicekci.tillora.invoice.domain.repository.InvoiceSettlementAllocationRepository;
import com.yigitcicekci.tillora.invoice.infrastructure.persistence.InvoiceLockRepository;
import com.yigitcicekci.tillora.invoice.infrastructure.persistence.InvoiceSettlementLockRepository;
import com.yigitcicekci.tillora.product.application.service.ProductReference;
import com.yigitcicekci.tillora.product.application.service.ProductService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.voucher.application.service.InvoiceSettlementDirection;
import com.yigitcicekci.tillora.voucher.application.service.InvoiceSettlementVoucherReference;
import com.yigitcicekci.tillora.voucher.application.service.InvoiceSettlementVoucherRequest;
import com.yigitcicekci.tillora.voucher.application.service.InvoiceSettlementVoucherService;
import com.yigitcicekci.tillora.voucher.application.service.InvoiceVoucherReference;
import com.yigitcicekci.tillora.voucher.application.service.InvoiceVoucherService;
import com.yigitcicekci.tillora.voucher.application.service.PurchaseInvoiceVoucherRequest;
import com.yigitcicekci.tillora.voucher.application.service.SalesInvoiceVoucherRequest;
import com.yigitcicekci.tillora.voucher.application.service.SettlementAccountReferenceType;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Currency;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.IntStream;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceService {

    private static final BigDecimal ZERO_MONEY = new BigDecimal("0.0000");
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineRepository invoiceLineRepository;
    private final InvoiceNumberGenerator invoiceNumberGenerator;
    private final InvoiceLockRepository invoiceLockRepository;
    private final InvoiceSettlementAllocationRepository invoiceSettlementAllocationRepository;
    private final InvoiceSettlementLockRepository invoiceSettlementLockRepository;
    private final CompanyService companyService;
    private final CurrentAccountService currentAccountService;
    private final CurrentAccountMovementService currentAccountMovementService;
    private final ProductService productService;
    private final InvoiceVoucherService invoiceVoucherService;
    private final InvoiceSettlementVoucherService invoiceSettlementVoucherService;
    private final AuditLogService auditLogService;

    public InvoiceService(
        InvoiceRepository invoiceRepository,
        InvoiceLineRepository invoiceLineRepository,
        InvoiceNumberGenerator invoiceNumberGenerator,
        InvoiceLockRepository invoiceLockRepository,
        InvoiceSettlementAllocationRepository invoiceSettlementAllocationRepository,
        InvoiceSettlementLockRepository invoiceSettlementLockRepository,
        CompanyService companyService,
        CurrentAccountService currentAccountService,
        CurrentAccountMovementService currentAccountMovementService,
        ProductService productService,
        InvoiceVoucherService invoiceVoucherService,
        InvoiceSettlementVoucherService invoiceSettlementVoucherService,
        AuditLogService auditLogService
    ) {
        this.invoiceRepository = invoiceRepository;
        this.invoiceLineRepository = invoiceLineRepository;
        this.invoiceNumberGenerator = invoiceNumberGenerator;
        this.invoiceLockRepository = invoiceLockRepository;
        this.invoiceSettlementAllocationRepository = invoiceSettlementAllocationRepository;
        this.invoiceSettlementLockRepository = invoiceSettlementLockRepository;
        this.companyService = companyService;
        this.currentAccountService = currentAccountService;
        this.currentAccountMovementService = currentAccountMovementService;
        this.productService = productService;
        this.invoiceVoucherService = invoiceVoucherService;
        this.invoiceSettlementVoucherService = invoiceSettlementVoucherService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public InvoiceResponse createSales(
        UUID companyId,
        UUID actorUserId,
        CreateSalesInvoiceRequest request
    ) {
        if (request == null || request.idempotencyKey() == null) {
            throw invalidRequest();
        }
        return create(
            companyId,
            actorUserId,
            InvoiceType.SALES,
            request.idempotencyKey(),
            request.currentAccountId(),
            request.invoiceDate(),
            request.dueDate(),
            request.currency(),
            request.lines()
        );
    }

    @Transactional
    public InvoiceResponse createPurchase(
        UUID companyId,
        UUID actorUserId,
        CreatePurchaseInvoiceRequest request
    ) {
        if (request == null || request.idempotencyKey() == null) {
            throw invalidRequest();
        }
        return create(
            companyId,
            actorUserId,
            InvoiceType.PURCHASE,
            request.idempotencyKey(),
            request.currentAccountId(),
            request.invoiceDate(),
            request.dueDate(),
            request.currency(),
            request.lines()
        );
    }

    private InvoiceResponse create(
        UUID companyId,
        UUID actorUserId,
        InvoiceType invoiceType,
        UUID idempotencyKey,
        UUID currentAccountId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        List<InvoiceLineRequest> requestedLines
    ) {
        NormalizedInput normalized = normalize(
            currentAccountId,
            invoiceDate,
            dueDate,
            currency,
            requestedLines
        );
        String fingerprint = InvoiceRequestFingerprint.create(
            invoiceType,
            normalized.currentAccountId(),
            normalized.requestedInvoiceDate(),
            normalized.dueDate(),
            normalized.requestedCurrency(),
            normalized.lines()
        );
        invoiceLockRepository.acquireIdempotency(companyId, idempotencyKey);
        Invoice existing = invoiceRepository.findByCompanyIdAndIdempotencyKey(
            companyId,
            idempotencyKey
        ).orElse(null);
        if (existing != null) {
            return existing(existing, companyId, invoiceType, fingerprint);
        }
        PreparedInvoice prepared = prepare(companyId, invoiceType, normalized);
        String invoiceNumber = invoiceNumberGenerator.next(
            companyId,
            invoiceType,
            prepared.invoiceDate().getYear()
        );
        Invoice invoice = invoiceType == InvoiceType.SALES
            ? Invoice.createSales(
                companyId,
                invoiceNumber,
                prepared.currentAccount().currentAccountId(),
                prepared.invoiceDate(),
                prepared.dueDate(),
                prepared.currency(),
                prepared.totals().subtotal(),
                prepared.totals().discountTotal(),
                prepared.totals().taxTotal(),
                prepared.totals().grandTotal(),
                prepared.totals().costTotal(),
                idempotencyKey,
                fingerprint,
                actorUserId
            )
            : Invoice.createPurchase(
                companyId,
                invoiceNumber,
                prepared.currentAccount().currentAccountId(),
                prepared.invoiceDate(),
                prepared.dueDate(),
                prepared.currency(),
                prepared.totals().subtotal(),
                prepared.totals().discountTotal(),
                prepared.totals().taxTotal(),
                prepared.totals().grandTotal(),
                prepared.totals().costTotal(),
                idempotencyKey,
                fingerprint,
                actorUserId
            );
        invoice = invoiceRepository.saveAndFlush(invoice);
        List<InvoiceLine> lines = saveLines(companyId, invoice.id(), prepared.lines());
        auditLogService.record(companyId, actorUserId, AuditAction.INVOICE_CREATE, "INVOICE", invoice.id());
        return InvoiceResponse.from(invoice, lines);
    }

    @Transactional
    public InvoiceResponse update(
        UUID companyId,
        UUID actorUserId,
        UUID invoiceId,
        UpdateInvoiceRequest request
    ) {
        if (request == null) {
            throw invalidRequest();
        }
        Invoice invoice = findForUpdate(companyId, invoiceId);
        validateDraft(invoice);
        NormalizedInput normalized = normalize(
            request.currentAccountId(),
            request.invoiceDate(),
            request.dueDate(),
            request.currency(),
            request.lines()
        );
        PreparedInvoice prepared = prepare(companyId, invoice.invoiceType(), normalized);
        if (prepared.invoiceDate().getYear() != invoice.invoiceDate().getYear()) {
            throw new BusinessException(
                "INVOICE_YEAR_CANNOT_CHANGE",
                "Invoice date cannot be moved to another calendar year.",
                HttpStatus.CONFLICT
            );
        }
        invoice.updateDraft(
            prepared.currentAccount().currentAccountId(),
            prepared.invoiceDate(),
            prepared.dueDate(),
            prepared.currency(),
            prepared.totals().subtotal(),
            prepared.totals().discountTotal(),
            prepared.totals().taxTotal(),
            prepared.totals().grandTotal(),
            prepared.totals().costTotal()
        );
        invoiceRepository.saveAndFlush(invoice);
        invoiceLineRepository.deleteAllByInvoiceIdAndCompanyId(invoice.id(), companyId);
        invoiceLineRepository.flush();
        List<InvoiceLine> lines = saveLines(companyId, invoice.id(), prepared.lines());
        auditLogService.record(companyId, actorUserId, AuditAction.INVOICE_UPDATE, "INVOICE", invoice.id());
        return InvoiceResponse.from(invoice, lines);
    }

    @Transactional(readOnly = true)
    public Page<InvoiceSummaryResponse> list(
        UUID companyId,
        InvoiceType invoiceType,
        InvoiceStatus status,
        LocalDate dateFrom,
        LocalDate dateTo,
        Pageable pageable
    ) {
        validateDateRange(dateFrom, dateTo);
        return invoiceRepository.findAllByFilters(
            companyId,
            invoiceType,
            status,
            dateFrom == null ? LocalDate.of(2000, 1, 1) : dateFrom,
            dateTo == null ? LocalDate.of(9999, 12, 31) : dateTo,
            pageable
        ).map(InvoiceSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public InvoiceResponse get(UUID companyId, UUID invoiceId) {
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)
            .orElseThrow(this::notFound);
        return response(companyId, invoice, lines(companyId, invoice.id()));
    }

    @Transactional
    public InvoiceResponse approve(UUID companyId, UUID actorUserId, UUID invoiceId) {
        Invoice invoice = findForUpdate(companyId, invoiceId);
        List<InvoiceLine> lines = lines(companyId, invoice.id());
        if (invoice.status() == InvoiceStatus.APPROVED) {
            return response(companyId, invoice, lines);
        }
        approveInvoice(companyId, actorUserId, invoice, lines);
        return response(companyId, invoice, lines);
    }

    @Transactional
    public InvoiceResponse approveWithSettlement(
        UUID companyId,
        UUID actorUserId,
        UUID invoiceId,
        CreateInvoiceSettlementRequest request
    ) {
        NormalizedSettlement settlement = normalizeSettlement(invoiceId, request);
        invoiceSettlementLockRepository.acquireIdempotency(companyId, settlement.idempotencyKey());
        InvoiceResponse existing = existingSettlement(companyId, invoiceId, settlement);
        if (existing != null) {
            return existing;
        }
        Invoice invoice = findForUpdate(companyId, invoiceId);
        List<InvoiceLine> lines = lines(companyId, invoice.id());
        validateDraft(invoice);
        validateSettlementAmount(invoice, ZERO_MONEY, settlement.amount());
        approveInvoice(companyId, actorUserId, invoice, lines);
        createSettlement(companyId, actorUserId, invoice, settlement);
        return InvoiceResponse.from(invoice, lines, settlement.amount());
    }

    @Transactional
    public InvoiceResponse settle(
        UUID companyId,
        UUID actorUserId,
        UUID invoiceId,
        CreateInvoiceSettlementRequest request
    ) {
        NormalizedSettlement settlement = normalizeSettlement(invoiceId, request);
        invoiceSettlementLockRepository.acquireIdempotency(companyId, settlement.idempotencyKey());
        InvoiceResponse existing = existingSettlement(companyId, invoiceId, settlement);
        if (existing != null) {
            return existing;
        }
        Invoice invoice = findForUpdate(companyId, invoiceId);
        if (invoice.status() != InvoiceStatus.APPROVED) {
            throw new BusinessException(
                "INVOICE_NOT_APPROVED",
                "Only approved invoices can be settled.",
                HttpStatus.CONFLICT
            );
        }
        BigDecimal paidAmount = paidAmount(companyId, invoice.id());
        validateSettlementAmount(invoice, paidAmount, settlement.amount());
        createSettlement(companyId, actorUserId, invoice, settlement);
        return InvoiceResponse.from(
            invoice,
            lines(companyId, invoice.id()),
            paidAmount.add(settlement.amount())
        );
    }

    private void approveInvoice(
        UUID companyId,
        UUID actorUserId,
        Invoice invoice,
        List<InvoiceLine> lines
    ) {
        validateDraft(invoice);
        Totals totals = calculatePersistedTotals(lines);
        validatePersistedTotals(invoice, totals);
        InvoiceVoucherReference voucher = invoice.invoiceType() == InvoiceType.SALES
            ? approveSales(companyId, actorUserId, invoice, lines)
            : approvePurchase(companyId, actorUserId, invoice, lines);
        invoice.approve(actorUserId, voucher.id());
        invoiceRepository.saveAndFlush(invoice);
        auditLogService.record(companyId, actorUserId, AuditAction.INVOICE_APPROVE, "INVOICE", invoice.id());
    }

    private InvoiceVoucherReference approveSales(
        UUID companyId,
        UUID actorUserId,
        Invoice invoice,
        List<InvoiceLine> lines
    ) {
        CurrentAccountPostingReference currentAccount =
            currentAccountService.findActiveSalesInvoiceAccount(companyId, invoice.currentAccountId());
        currentAccountMovementService.recordSalesInvoice(
            companyId,
            currentAccount.currentAccountId(),
            invoice.id(),
            invoice.invoiceDate(),
            invoice.dueDate(),
            invoice.grandTotal()
        );
        return invoiceVoucherService.createSalesInvoiceVoucher(
            companyId,
            actorUserId,
            new SalesInvoiceVoucherRequest(
                invoice.id(),
                invoice.invoiceNumber(),
                currentAccount.currentAccountId(),
                currentAccount.chartOfAccountId(),
                invoice.invoiceDate(),
                invoice.dueDate(),
                invoice.currency(),
                invoice.subtotal().subtract(invoice.discountTotal()),
                invoice.taxTotal(),
                invoice.grandTotal(),
                invoice.costTotal()
            )
        );
    }

    private InvoiceVoucherReference approvePurchase(
        UUID companyId,
        UUID actorUserId,
        Invoice invoice,
        List<InvoiceLine> lines
    ) {
        CurrentAccountPostingReference currentAccount =
            currentAccountService.findActivePurchaseInvoiceAccount(
                companyId,
                invoice.currentAccountId()
            );
        currentAccountMovementService.recordPurchaseInvoice(
            companyId,
            currentAccount.currentAccountId(),
            invoice.id(),
            invoice.invoiceDate(),
            invoice.dueDate(),
            invoice.grandTotal()
        );
        return invoiceVoucherService.createPurchaseInvoiceVoucher(
            companyId,
            actorUserId,
            new PurchaseInvoiceVoucherRequest(
                invoice.id(),
                invoice.invoiceNumber(),
                currentAccount.currentAccountId(),
                currentAccount.chartOfAccountId(),
                invoice.invoiceDate(),
                invoice.dueDate(),
                invoice.currency(),
                invoice.subtotal().subtract(invoice.discountTotal()),
                invoice.taxTotal(),
                invoice.grandTotal()
            )
        );
    }

    private PreparedInvoice prepare(
        UUID companyId,
        InvoiceType invoiceType,
        NormalizedInput input
    ) {
        CompanyFinancialContext context = companyService.financialContext(companyId);
        LocalDate invoiceDate = input.requestedInvoiceDate() == null
            ? LocalDate.now(ZoneId.of(context.timezone()))
            : input.requestedInvoiceDate();
        validateDate(invoiceDate);
        if (input.dueDate() != null && input.dueDate().isBefore(invoiceDate)) {
            throw new BusinessException(
                "INVOICE_DUE_DATE_INVALID",
                "Invoice due date cannot be before invoice date.",
                HttpStatus.BAD_REQUEST
            );
        }
        String currency = input.requestedCurrency() == null
            ? context.currency()
            : input.requestedCurrency();
        if (!context.currency().equals(currency)) {
            throw new BusinessException(
                "INVOICE_CURRENCY_NOT_SUPPORTED",
                "The first invoice version supports only the company currency.",
                HttpStatus.BAD_REQUEST
            );
        }
        CurrentAccountPostingReference currentAccount = invoiceType == InvoiceType.SALES
            ? currentAccountService.findActiveSalesInvoiceAccount(companyId, input.currentAccountId())
            : currentAccountService.findActivePurchaseInvoiceAccount(companyId, input.currentAccountId());
        List<PreparedLine> lines = IntStream.range(0, input.lines().size())
            .mapToObj(index -> prepareLine(
                companyId,
                invoiceType,
                index + 1,
                input.lines().get(index)
            ))
            .toList();
        Totals totals = calculatePreparedTotals(lines);
        if (totals.grandTotal().signum() <= 0) {
            throw new BusinessException(
                "INVOICE_TOTAL_INVALID",
                "Invoice grand total must be greater than zero.",
                HttpStatus.BAD_REQUEST
            );
        }
        return new PreparedInvoice(
            currentAccount,
            invoiceDate,
            input.dueDate(),
            currency,
            lines,
            totals
        );
    }

    private PreparedLine prepareLine(
        UUID companyId,
        InvoiceType invoiceType,
        int lineNumber,
        InvoiceLineRequest request
    ) {
        ProductReference product = productService.findActiveProduct(companyId, request.productId());
        if (request.vatRate().compareTo(product.vatRate()) != 0) {
            throw new BusinessException(
                "INVOICE_VAT_RATE_MISMATCH",
                "Invoice VAT rate must match the product VAT rate.",
                HttpStatus.BAD_REQUEST
            );
        }
        BigDecimal gross = money(request.quantity().multiply(request.unitPrice()));
        BigDecimal discountAmount = money(
            gross.multiply(request.discountRate()).divide(HUNDRED, 8, RoundingMode.HALF_UP)
        );
        BigDecimal taxableAmount = gross.subtract(discountAmount);
        BigDecimal vatAmount = money(
            taxableAmount.multiply(product.vatRate()).divide(HUNDRED, 8, RoundingMode.HALF_UP)
        );
        BigDecimal lineTotal = taxableAmount.add(vatAmount);
        BigDecimal unitCost = invoiceType == InvoiceType.SALES
            ? product.purchasePrice()
            : taxableAmount.divide(request.quantity(), 4, RoundingMode.HALF_UP);
        BigDecimal costTotal = invoiceType == InvoiceType.SALES
            ? money(request.quantity().multiply(product.purchasePrice()))
            : taxableAmount;
        String description = request.description() == null
            ? product.name()
            : request.description();
        return new PreparedLine(
            lineNumber,
            product.id(),
            product.code(),
            description,
            request.quantity(),
            product.unit(),
            request.unitPrice(),
            request.discountRate(),
            discountAmount,
            product.vatRate(),
            vatAmount,
            lineTotal,
            unitCost,
            costTotal,
            gross
        );
    }

    private List<InvoiceLine> saveLines(
        UUID companyId,
        UUID invoiceId,
        List<PreparedLine> preparedLines
    ) {
        List<InvoiceLine> lines = preparedLines.stream()
            .map(line -> InvoiceLine.create(
                companyId,
                invoiceId,
                line.lineNumber(),
                line.productId(),
                line.productCode(),
                line.description(),
                line.quantity(),
                line.unit(),
                line.unitPrice(),
                line.discountRate(),
                line.discountAmount(),
                line.vatRate(),
                line.vatAmount(),
                line.lineTotal(),
                line.unitCost(),
                line.costTotal()
            ))
            .toList();
        return invoiceLineRepository.saveAllAndFlush(lines);
    }

    private NormalizedInput normalize(
        UUID currentAccountId,
        LocalDate requestedInvoiceDate,
        LocalDate dueDate,
        String requestedCurrency,
        List<InvoiceLineRequest> requests
    ) {
        if (currentAccountId == null
            || requests == null
            || requests.isEmpty()
            || requests.size() > 100) {
            throw invalidRequest();
        }
        if (requestedInvoiceDate != null) {
            validateDate(requestedInvoiceDate);
        }
        String currency = normalizeCurrency(requestedCurrency);
        List<InvoiceLineRequest> lines = requests.stream()
            .map(this::normalizeLine)
            .toList();
        return new NormalizedInput(
            currentAccountId,
            requestedInvoiceDate,
            dueDate,
            currency,
            lines
        );
    }

    private InvoiceLineRequest normalizeLine(InvoiceLineRequest request) {
        if (request == null || request.productId() == null) {
            throw invalidRequest();
        }
        BigDecimal quantity = quantity(request.quantity());
        BigDecimal unitPrice = moneyInput(request.unitPrice(), "INVOICE_UNIT_PRICE_INVALID");
        BigDecimal discountRate = rate(request.discountRate(), "INVOICE_DISCOUNT_RATE_INVALID");
        BigDecimal vatRate = rate(request.vatRate(), "INVOICE_VAT_RATE_INVALID");
        String description = normalizeDescription(request.description());
        return new InvoiceLineRequest(
            request.productId(),
            description,
            quantity,
            unitPrice,
            discountRate,
            vatRate
        );
    }

    private Totals calculatePreparedTotals(List<PreparedLine> lines) {
        BigDecimal subtotal = ZERO_MONEY;
        BigDecimal discount = ZERO_MONEY;
        BigDecimal tax = ZERO_MONEY;
        BigDecimal grand = ZERO_MONEY;
        BigDecimal cost = ZERO_MONEY;
        for (PreparedLine line : lines) {
            subtotal = subtotal.add(line.gross());
            discount = discount.add(line.discountAmount());
            tax = tax.add(line.vatAmount());
            grand = grand.add(line.lineTotal());
            cost = cost.add(line.costTotal());
        }
        return new Totals(subtotal, discount, tax, grand, cost);
    }

    private Totals calculatePersistedTotals(List<InvoiceLine> lines) {
        if (lines.isEmpty() || lines.size() > 100) {
            throw new BusinessException(
                "INVOICE_LINE_COUNT_INVALID",
                "Invoice must contain between 1 and 100 lines.",
                HttpStatus.CONFLICT
            );
        }
        BigDecimal subtotal = ZERO_MONEY;
        BigDecimal discount = ZERO_MONEY;
        BigDecimal tax = ZERO_MONEY;
        BigDecimal grand = ZERO_MONEY;
        BigDecimal cost = ZERO_MONEY;
        for (InvoiceLine line : lines) {
            subtotal = subtotal.add(line.lineTotal())
                .add(line.discountAmount())
                .subtract(line.vatAmount());
            discount = discount.add(line.discountAmount());
            tax = tax.add(line.vatAmount());
            grand = grand.add(line.lineTotal());
            cost = cost.add(line.costTotal());
        }
        return new Totals(subtotal, discount, tax, grand, cost);
    }

    private void validatePersistedTotals(Invoice invoice, Totals totals) {
        if (invoice.subtotal().compareTo(totals.subtotal()) != 0
            || invoice.discountTotal().compareTo(totals.discountTotal()) != 0
            || invoice.taxTotal().compareTo(totals.taxTotal()) != 0
            || invoice.grandTotal().compareTo(totals.grandTotal()) != 0
            || invoice.costTotal().compareTo(totals.costTotal()) != 0) {
            throw new BusinessException(
                "INVOICE_TOTALS_INCONSISTENT",
                "Invoice totals do not match its lines.",
                HttpStatus.CONFLICT
            );
        }
    }

    private InvoiceResponse existing(
        Invoice invoice,
        UUID companyId,
        InvoiceType invoiceType,
        String fingerprint
    ) {
        if (invoice.invoiceType() != invoiceType
            || !invoice.requestFingerprint().equals(fingerprint)) {
            throw new BusinessException(
                "IDEMPOTENCY_KEY_REUSED",
                "Idempotency key was already used for another invoice request.",
                HttpStatus.CONFLICT
            );
        }
        return response(companyId, invoice, lines(companyId, invoice.id()));
    }

    private void createSettlement(
        UUID companyId,
        UUID actorUserId,
        Invoice invoice,
        NormalizedSettlement settlement
    ) {
        UUID voucherIdempotencyKey = UUID.nameUUIDFromBytes(
            ("invoice-settlement:" + companyId + ":" + settlement.idempotencyKey())
                .getBytes(StandardCharsets.UTF_8)
        );
        InvoiceSettlementVoucherReference voucher = invoiceSettlementVoucherService.createAndApprove(
            companyId,
            actorUserId,
            invoice.invoiceType() == InvoiceType.SALES
                ? InvoiceSettlementDirection.COLLECTION
                : InvoiceSettlementDirection.PAYMENT,
            new InvoiceSettlementVoucherRequest(
                voucherIdempotencyKey,
                invoice.currentAccountId(),
                SettlementAccountReferenceType.valueOf(settlement.settlementAccountType().name()),
                settlement.settlementAccountId(),
                settlement.amount(),
                settlement.voucherDate(),
                settlement.movementNote(),
                settlement.documentNumber() == null
                    ? invoice.invoiceNumber()
                    : settlement.documentNumber()
            )
        );
        invoiceSettlementAllocationRepository.saveAndFlush(InvoiceSettlementAllocation.create(
            companyId,
            invoice.id(),
            voucher.id(),
            settlement.idempotencyKey(),
            settlement.requestFingerprint(),
            settlement.amount()
        ));
    }

    private InvoiceResponse existingSettlement(
        UUID companyId,
        UUID invoiceId,
        NormalizedSettlement settlement
    ) {
        InvoiceSettlementAllocation existing = invoiceSettlementAllocationRepository
            .findByCompanyIdAndIdempotencyKey(companyId, settlement.idempotencyKey())
            .orElse(null);
        if (existing == null) {
            return null;
        }
        if (!existing.invoiceId().equals(invoiceId)
            || !existing.requestFingerprint().equals(settlement.requestFingerprint())) {
            throw new BusinessException(
                "IDEMPOTENCY_KEY_REUSED",
                "Idempotency key was already used for another invoice settlement request.",
                HttpStatus.CONFLICT
            );
        }
        Invoice invoice = invoiceRepository.findByIdAndCompanyId(invoiceId, companyId)
            .orElseThrow(this::notFound);
        return response(companyId, invoice, lines(companyId, invoice.id()));
    }

    private void validateSettlementAmount(
        Invoice invoice,
        BigDecimal paidAmount,
        BigDecimal settlementAmount
    ) {
        BigDecimal remainingAmount = invoice.grandTotal().subtract(paidAmount);
        if (settlementAmount.compareTo(remainingAmount) > 0) {
            throw new BusinessException(
                "INVOICE_SETTLEMENT_EXCEEDS_REMAINING_AMOUNT",
                "Invoice settlement amount cannot exceed the remaining amount.",
                HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
    }

    private NormalizedSettlement normalizeSettlement(
        UUID invoiceId,
        CreateInvoiceSettlementRequest request
    ) {
        if (request == null
            || request.idempotencyKey() == null
            || request.settlementAccountType() == null
            || request.settlementAccountId() == null
            || request.amount() == null
            || request.amount().signum() <= 0) {
            throw new BusinessException(
                "INVOICE_SETTLEMENT_REQUEST_INVALID",
                "Invoice settlement request is incomplete.",
                HttpStatus.BAD_REQUEST
            );
        }
        BigDecimal amount;
        try {
            amount = request.amount().setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                "INVOICE_SETTLEMENT_AMOUNT_INVALID",
                "Invoice settlement amount supports at most 4 decimal places.",
                HttpStatus.BAD_REQUEST
            );
        }
        if (request.voucherDate() != null) {
            validateDate(request.voucherDate());
        }
        String movementNote = normalizeSettlementText(request.movementNote(), 500);
        String documentNumber = normalizeSettlementText(request.documentNumber(), 80);
        CreateInvoiceSettlementRequest normalized = new CreateInvoiceSettlementRequest(
            request.idempotencyKey(),
            request.settlementAccountType(),
            request.settlementAccountId(),
            amount,
            request.voucherDate(),
            movementNote,
            documentNumber
        );
        return new NormalizedSettlement(
            normalized.idempotencyKey(),
            normalized.settlementAccountType(),
            normalized.settlementAccountId(),
            normalized.amount(),
            normalized.voucherDate(),
            normalized.movementNote(),
            normalized.documentNumber(),
            InvoiceSettlementRequestFingerprint.create(invoiceId, normalized)
        );
    }

    private String normalizeSettlementText(String value, int maxLength) {
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

    private InvoiceResponse response(
        UUID companyId,
        Invoice invoice,
        List<InvoiceLine> lines
    ) {
        return InvoiceResponse.from(invoice, lines, paidAmount(companyId, invoice.id()));
    }

    private BigDecimal paidAmount(UUID companyId, UUID invoiceId) {
        return invoiceSettlementAllocationRepository.sumApprovedAmount(companyId, invoiceId)
            .setScale(4);
    }

    private void validateDraft(Invoice invoice) {
        if (invoice.status() != InvoiceStatus.DRAFT) {
            throw new BusinessException(
                "INVOICE_NOT_DRAFT",
                "Only draft invoices can be changed or approved.",
                HttpStatus.CONFLICT
            );
        }
    }

    private Invoice findForUpdate(UUID companyId, UUID invoiceId) {
        return invoiceRepository.findForUpdate(invoiceId, companyId)
            .orElseThrow(this::notFound);
    }

    private List<InvoiceLine> lines(UUID companyId, UUID invoiceId) {
        return invoiceLineRepository.findByInvoiceIdAndCompanyIdOrderByLineNumberAsc(
            invoiceId,
            companyId
        );
    }

    private BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new BusinessException(
                "INVOICE_QUANTITY_INVALID",
                "Invoice quantity must be greater than zero.",
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            return value.setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                "INVOICE_QUANTITY_INVALID",
                "Invoice quantity supports at most 6 decimal places.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private BigDecimal moneyInput(BigDecimal value, String code) {
        if (value == null || value.signum() < 0) {
            throw new BusinessException(code, "Invoice monetary value is invalid.", HttpStatus.BAD_REQUEST);
        }
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                code,
                "Invoice monetary values support at most 4 decimal places.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private BigDecimal rate(BigDecimal value, String code) {
        if (value == null || value.signum() < 0 || value.compareTo(HUNDRED) > 0) {
            throw new BusinessException(code, "Invoice rate must be between 0 and 100.", HttpStatus.BAD_REQUEST);
        }
        try {
            return value.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                code,
                "Invoice rates support at most 2 decimal places.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP);
    }

    private String normalizeDescription(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > 500) {
            throw new BusinessException(
                "INVOICE_DESCRIPTION_INVALID",
                "Invoice line description is too long.",
                HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
    }

    private String normalizeCurrency(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(
                "INVOICE_CURRENCY_INVALID",
                "Invoice currency is invalid.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateDateRange(LocalDate dateFrom, LocalDate dateTo) {
        if (dateFrom != null) {
            validateDate(dateFrom);
        }
        if (dateTo != null) {
            validateDate(dateTo);
        }
        if (dateFrom != null && dateTo != null && dateFrom.isAfter(dateTo)) {
            throw new BusinessException(
                "INVOICE_DATE_RANGE_INVALID",
                "Invoice start date cannot be after end date.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateDate(LocalDate date) {
        if (date.getYear() < 2000 || date.getYear() > 9999) {
            throw new BusinessException(
                "INVOICE_DATE_INVALID",
                "Invoice date is outside the supported range.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private BusinessException invalidRequest() {
        return new BusinessException(
            "INVOICE_REQUEST_INVALID",
            "Invoice request is incomplete.",
            HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException notFound() {
        return new BusinessException("INVOICE_NOT_FOUND", "Invoice not found.", HttpStatus.NOT_FOUND);
    }

    private record NormalizedInput(
        UUID currentAccountId,
        LocalDate requestedInvoiceDate,
        LocalDate dueDate,
        String requestedCurrency,
        List<InvoiceLineRequest> lines
    ) {
    }

    private record NormalizedSettlement(
        UUID idempotencyKey,
        com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceSettlementAccountType settlementAccountType,
        UUID settlementAccountId,
        BigDecimal amount,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        String requestFingerprint
    ) {
    }

    private record PreparedInvoice(
        CurrentAccountPostingReference currentAccount,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        List<PreparedLine> lines,
        Totals totals
    ) {
    }

    private record PreparedLine(
        int lineNumber,
        UUID productId,
        String productCode,
        String description,
        BigDecimal quantity,
        String unit,
        BigDecimal unitPrice,
        BigDecimal discountRate,
        BigDecimal discountAmount,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal lineTotal,
        BigDecimal unitCost,
        BigDecimal costTotal,
        BigDecimal gross
    ) {
    }

    private record Totals(
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal costTotal
    ) {
    }
}
