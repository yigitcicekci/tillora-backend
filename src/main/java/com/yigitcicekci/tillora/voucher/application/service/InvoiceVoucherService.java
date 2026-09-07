package com.yigitcicekci.tillora.voucher.application.service;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.chartofaccount.application.service.ChartOfAccountService;
import com.yigitcicekci.tillora.chartofaccount.application.service.PostingAccountReference;
import com.yigitcicekci.tillora.chartofaccount.application.service.PurchaseInvoicePostingAccounts;
import com.yigitcicekci.tillora.chartofaccount.application.service.SalesInvoicePostingAccounts;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
import com.yigitcicekci.tillora.voucher.domain.entity.VoucherLine;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherSourceType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherLineRepository;
import com.yigitcicekci.tillora.voucher.domain.repository.VoucherRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceVoucherService {

    private static final BigDecimal ZERO = new BigDecimal("0.0000");

    private final VoucherRepository voucherRepository;
    private final VoucherLineRepository voucherLineRepository;
    private final VoucherNumberGenerator voucherNumberGenerator;
    private final ChartOfAccountService chartOfAccountService;
    private final AuditLogService auditLogService;

    public InvoiceVoucherService(
        VoucherRepository voucherRepository,
        VoucherLineRepository voucherLineRepository,
        VoucherNumberGenerator voucherNumberGenerator,
        ChartOfAccountService chartOfAccountService,
        AuditLogService auditLogService
    ) {
        this.voucherRepository = voucherRepository;
        this.voucherLineRepository = voucherLineRepository;
        this.voucherNumberGenerator = voucherNumberGenerator;
        this.chartOfAccountService = chartOfAccountService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public InvoiceVoucherReference createSalesInvoiceVoucher(
        UUID companyId,
        UUID actorUserId,
        SalesInvoiceVoucherRequest request
    ) {
        validate(request);
        Voucher existing = voucherRepository.findByCompanyIdAndSourceTypeAndSourceId(
            companyId,
            VoucherSourceType.SALES_INVOICE,
            request.invoiceId()
        ).orElse(null);
        if (existing != null) {
            if (existing.status() != VoucherStatus.APPROVED) {
                throw new BusinessException(
                    "INVOICE_VOUCHER_STATE_INVALID",
                    "Invoice accounting voucher is not approved.",
                    HttpStatus.CONFLICT
                );
            }
            return new InvoiceVoucherReference(existing.id(), existing.voucherNumber());
        }
        PostingAccountReference receivable = chartOfAccountService
            .findActivePostingAccounts(companyId, List.of(request.currentAccountChartAccountId()))
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                "INVOICE_CURRENT_ACCOUNT_INVALID",
                "Sales invoice requires an active receivable posting account.",
                HttpStatus.BAD_REQUEST
            ));
        SalesInvoicePostingAccounts accounts =
            chartOfAccountService.findSalesInvoicePostingAccounts(companyId);
        BigDecimal voucherTotal = request.grandTotal().add(request.costTotal());
        String voucherNumber = voucherNumberGenerator.next(
            companyId,
            VoucherType.OFFSET,
            request.invoiceDate().getYear()
        );
        Voucher voucher = voucherRepository.saveAndFlush(Voucher.createSalesInvoice(
            companyId,
            voucherNumber,
            request.invoiceDate(),
            "Sales invoice " + request.invoiceNumber(),
            request.invoiceNumber(),
            request.currency(),
            actorUserId,
            voucherTotal,
            voucherTotal,
            request.invoiceId()
        ));
        List<VoucherLine> lines = voucherLineRepository.saveAllAndFlush(lines(
            companyId,
            voucher.id(),
            request,
            receivable,
            accounts
        ));
        voucher.approve(actorUserId);
        voucherRepository.saveAndFlush(voucher);
        auditLogService.record(companyId, actorUserId, AuditAction.VOUCHER_CREATE, "VOUCHER", voucher.id());
        auditLogService.record(companyId, actorUserId, AuditAction.VOUCHER_APPROVE, "VOUCHER", voucher.id());
        return new InvoiceVoucherReference(voucher.id(), voucher.voucherNumber());
    }

    @Transactional
    public InvoiceVoucherReference createPurchaseInvoiceVoucher(
        UUID companyId,
        UUID actorUserId,
        PurchaseInvoiceVoucherRequest request
    ) {
        validate(request);
        Voucher existing = voucherRepository.findByCompanyIdAndSourceTypeAndSourceId(
            companyId,
            VoucherSourceType.PURCHASE_INVOICE,
            request.invoiceId()
        ).orElse(null);
        if (existing != null) {
            if (existing.status() != VoucherStatus.APPROVED) {
                throw new BusinessException(
                    "INVOICE_VOUCHER_STATE_INVALID",
                    "Invoice accounting voucher is not approved.",
                    HttpStatus.CONFLICT
                );
            }
            return new InvoiceVoucherReference(existing.id(), existing.voucherNumber());
        }
        PostingAccountReference payable = chartOfAccountService
            .findActivePostingAccounts(companyId, List.of(request.currentAccountChartAccountId()))
            .stream()
            .findFirst()
            .orElseThrow(() -> new BusinessException(
                "INVOICE_CURRENT_ACCOUNT_INVALID",
                "Purchase invoice requires an active payable posting account.",
                HttpStatus.BAD_REQUEST
            ));
        PurchaseInvoicePostingAccounts accounts =
            chartOfAccountService.findPurchaseInvoicePostingAccounts(companyId);
        String voucherNumber = voucherNumberGenerator.next(
            companyId,
            VoucherType.OFFSET,
            request.invoiceDate().getYear()
        );
        Voucher voucher = voucherRepository.saveAndFlush(Voucher.createPurchaseInvoice(
            companyId,
            voucherNumber,
            request.invoiceDate(),
            "Purchase invoice " + request.invoiceNumber(),
            request.invoiceNumber(),
            request.currency(),
            actorUserId,
            request.grandTotal(),
            request.grandTotal(),
            request.invoiceId()
        ));
        voucherLineRepository.saveAllAndFlush(purchaseLines(
            companyId,
            voucher.id(),
            request,
            payable,
            accounts
        ));
        voucher.approve(actorUserId);
        voucherRepository.saveAndFlush(voucher);
        auditLogService.record(companyId, actorUserId, AuditAction.VOUCHER_CREATE, "VOUCHER", voucher.id());
        auditLogService.record(companyId, actorUserId, AuditAction.VOUCHER_APPROVE, "VOUCHER", voucher.id());
        return new InvoiceVoucherReference(voucher.id(), voucher.voucherNumber());
    }

    private List<VoucherLine> lines(
        UUID companyId,
        UUID voucherId,
        SalesInvoiceVoucherRequest request,
        PostingAccountReference receivable,
        SalesInvoicePostingAccounts accounts
    ) {
        List<VoucherLine> lines = new ArrayList<>();
        String note = "Sales invoice " + request.invoiceNumber();
        lines.add(VoucherLine.create(
            companyId,
            voucherId,
            lines.size() + 1,
            receivable.id(),
            request.currentAccountId(),
            receivable.code(),
            receivable.name(),
            note,
            request.grandTotal(),
            ZERO,
            null,
            request.dueDate()
        ));
        lines.add(creditLine(
            companyId,
            voucherId,
            lines.size() + 1,
            accounts.domesticSales(),
            note,
            request.netSales()
        ));
        if (request.taxTotal().signum() > 0) {
            lines.add(creditLine(
                companyId,
                voucherId,
                lines.size() + 1,
                accounts.calculatedVat(),
                note,
                request.taxTotal()
            ));
        }
        if (request.costTotal().signum() > 0) {
            lines.add(VoucherLine.create(
                companyId,
                voucherId,
                lines.size() + 1,
                accounts.costOfGoodsSold().id(),
                accounts.costOfGoodsSold().code(),
                accounts.costOfGoodsSold().name(),
                note,
                request.costTotal(),
                ZERO,
                null,
                null
            ));
            lines.add(creditLine(
                companyId,
                voucherId,
                lines.size() + 1,
                accounts.tradeGoods(),
                note,
                request.costTotal()
            ));
        }
        return lines;
    }

    private VoucherLine creditLine(
        UUID companyId,
        UUID voucherId,
        int lineNumber,
        PostingAccountReference account,
        String note,
        BigDecimal amount
    ) {
        return VoucherLine.create(
            companyId,
            voucherId,
            lineNumber,
            account.id(),
            account.code(),
            account.name(),
            note,
            ZERO,
            amount,
            null,
            null
        );
    }

    private List<VoucherLine> purchaseLines(
        UUID companyId,
        UUID voucherId,
        PurchaseInvoiceVoucherRequest request,
        PostingAccountReference payable,
        PurchaseInvoicePostingAccounts accounts
    ) {
        List<VoucherLine> lines = new ArrayList<>();
        String note = "Purchase invoice " + request.invoiceNumber();
        lines.add(VoucherLine.create(
            companyId,
            voucherId,
            lines.size() + 1,
            accounts.tradeGoods().id(),
            accounts.tradeGoods().code(),
            accounts.tradeGoods().name(),
            note,
            request.netPurchase(),
            ZERO,
            null,
            null
        ));
        if (request.taxTotal().signum() > 0) {
            lines.add(VoucherLine.create(
                companyId,
                voucherId,
                lines.size() + 1,
                accounts.deductibleVat().id(),
                accounts.deductibleVat().code(),
                accounts.deductibleVat().name(),
                note,
                request.taxTotal(),
                ZERO,
                null,
                null
            ));
        }
        lines.add(VoucherLine.create(
            companyId,
            voucherId,
            lines.size() + 1,
            payable.id(),
            request.currentAccountId(),
            payable.code(),
            payable.name(),
            note,
            ZERO,
            request.grandTotal(),
            null,
            request.dueDate()
        ));
        return lines;
    }

    private void validate(SalesInvoiceVoucherRequest request) {
        if (request == null
            || request.invoiceId() == null
            || request.invoiceNumber() == null
            || request.currentAccountId() == null
            || request.currentAccountChartAccountId() == null
            || request.invoiceDate() == null
            || request.currency() == null
            || request.netSales() == null
            || request.taxTotal() == null
            || request.grandTotal() == null
            || request.costTotal() == null
            || request.netSales().signum() <= 0
            || request.taxTotal().signum() < 0
            || request.grandTotal().signum() <= 0
            || request.costTotal().signum() < 0
            || request.netSales().add(request.taxTotal()).compareTo(request.grandTotal()) != 0) {
            throw new BusinessException(
                "INVOICE_VOUCHER_REQUEST_INVALID",
                "Sales invoice accounting values are invalid.",
                HttpStatus.CONFLICT
            );
        }
    }

    private void validate(PurchaseInvoiceVoucherRequest request) {
        if (request == null
            || request.invoiceId() == null
            || request.invoiceNumber() == null
            || request.currentAccountId() == null
            || request.currentAccountChartAccountId() == null
            || request.invoiceDate() == null
            || request.currency() == null
            || request.netPurchase() == null
            || request.taxTotal() == null
            || request.grandTotal() == null
            || request.netPurchase().signum() <= 0
            || request.taxTotal().signum() < 0
            || request.grandTotal().signum() <= 0
            || request.netPurchase().add(request.taxTotal()).compareTo(request.grandTotal()) != 0) {
            throw new BusinessException(
                "INVOICE_VOUCHER_REQUEST_INVALID",
                "Purchase invoice accounting values are invalid.",
                HttpStatus.CONFLICT
            );
        }
    }
}
