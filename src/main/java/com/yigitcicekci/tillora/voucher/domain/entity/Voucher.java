package com.yigitcicekci.tillora.voucher.domain.entity;

import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherSourceType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "vouchers")
public class Voucher {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 24)
    private String voucherNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherType voucherType;

    @Column(nullable = false)
    private LocalDate voucherDate;

    @Column(length = 500)
    private String movementNote;

    @Column(length = 80)
    private String documentNumber;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal exchangeRate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private VoucherStatus status;

    @Column(nullable = false)
    private UUID createdBy;

    private UUID idempotencyKey;

    @Column(length = 64)
    private String requestFingerprint;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private VoucherSourceType sourceType;

    private UUID sourceId;

    private UUID approvedBy;

    private UUID cancelledBy;

    @Column(length = 500)
    private String cancellationReason;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalDebit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal totalCredit;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant approvedAt;

    private Instant cancelledAt;

    @Version
    private long version;

    protected Voucher() {
    }

    private Voucher(
        UUID companyId,
        String voucherNumber,
        VoucherType voucherType,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        String currency,
        BigDecimal exchangeRate,
        UUID createdBy,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        UUID idempotencyKey,
        String requestFingerprint,
        VoucherSourceType sourceType,
        UUID sourceId
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.voucherNumber = voucherNumber;
        this.voucherType = voucherType;
        this.voucherDate = voucherDate;
        this.movementNote = movementNote;
        this.documentNumber = documentNumber;
        this.currency = currency;
        this.exchangeRate = exchangeRate;
        this.status = VoucherStatus.DRAFT;
        this.createdBy = createdBy;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Voucher create(
        UUID companyId,
        String voucherNumber,
        VoucherType voucherType,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        String currency,
        BigDecimal exchangeRate,
        UUID createdBy,
        BigDecimal totalDebit,
        BigDecimal totalCredit
    ) {
        return new Voucher(
            companyId,
            voucherNumber,
            voucherType,
            voucherDate,
            movementNote,
            documentNumber,
            currency,
            exchangeRate,
            createdBy,
            totalDebit,
            totalCredit,
            null,
            null,
            null,
            null
        );
    }

    public static Voucher createGuided(
        UUID companyId,
        String voucherNumber,
        VoucherType voucherType,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        String currency,
        BigDecimal exchangeRate,
        UUID createdBy,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        UUID idempotencyKey,
        String requestFingerprint
    ) {
        return new Voucher(
            companyId,
            voucherNumber,
            voucherType,
            voucherDate,
            movementNote,
            documentNumber,
            currency,
            exchangeRate,
            createdBy,
            totalDebit,
            totalCredit,
            idempotencyKey,
            requestFingerprint,
            null,
            null
        );
    }

    public static Voucher createManual(
        UUID companyId,
        String voucherNumber,
        VoucherType voucherType,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        String currency,
        BigDecimal exchangeRate,
        UUID createdBy,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        UUID idempotencyKey,
        String requestFingerprint
    ) {
        return new Voucher(
            companyId,
            voucherNumber,
            voucherType,
            voucherDate,
            movementNote,
            documentNumber,
            currency,
            exchangeRate,
            createdBy,
            totalDebit,
            totalCredit,
            idempotencyKey,
            requestFingerprint,
            null,
            null
        );
    }

    public static Voucher createSalesInvoice(
        UUID companyId,
        String voucherNumber,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        String currency,
        UUID createdBy,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        UUID invoiceId
    ) {
        return new Voucher(
            companyId,
            voucherNumber,
            VoucherType.OFFSET,
            voucherDate,
            movementNote,
            documentNumber,
            currency,
            new BigDecimal("1.00000000"),
            createdBy,
            totalDebit,
            totalCredit,
            null,
            null,
            VoucherSourceType.SALES_INVOICE,
            invoiceId
        );
    }

    public static Voucher createPurchaseInvoice(
        UUID companyId,
        String voucherNumber,
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        String currency,
        UUID createdBy,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        UUID invoiceId
    ) {
        return new Voucher(
            companyId,
            voucherNumber,
            VoucherType.OFFSET,
            voucherDate,
            movementNote,
            documentNumber,
            currency,
            new BigDecimal("1.00000000"),
            createdBy,
            totalDebit,
            totalCredit,
            null,
            null,
            VoucherSourceType.PURCHASE_INVOICE,
            invoiceId
        );
    }

    public void updateDraft(
        LocalDate voucherDate,
        String movementNote,
        String documentNumber,
        String currency,
        BigDecimal totalDebit,
        BigDecimal totalCredit
    ) {
        this.voucherDate = voucherDate;
        this.movementNote = movementNote;
        this.documentNumber = documentNumber;
        this.currency = currency;
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
        this.updatedAt = Instant.now();
    }

    public void approve(UUID actorUserId) {
        Instant now = Instant.now();
        this.status = VoucherStatus.APPROVED;
        this.approvedBy = actorUserId;
        this.approvedAt = now;
        this.updatedAt = now;
    }

    public void cancel(UUID actorUserId, String reason) {
        Instant now = Instant.now();
        this.status = VoucherStatus.CANCELLED;
        this.cancelledBy = actorUserId;
        this.cancellationReason = reason;
        this.cancelledAt = now;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public String voucherNumber() {
        return voucherNumber;
    }

    public VoucherType voucherType() {
        return voucherType;
    }

    public LocalDate voucherDate() {
        return voucherDate;
    }

    public String movementNote() {
        return movementNote;
    }

    public String documentNumber() {
        return documentNumber;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal exchangeRate() {
        return exchangeRate;
    }

    public VoucherStatus status() {
        return status;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public UUID approvedBy() {
        return approvedBy;
    }

    public UUID idempotencyKey() {
        return idempotencyKey;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public VoucherSourceType sourceType() {
        return sourceType;
    }

    public UUID sourceId() {
        return sourceId;
    }

    public UUID cancelledBy() {
        return cancelledBy;
    }

    public String cancellationReason() {
        return cancellationReason;
    }

    public BigDecimal totalDebit() {
        return totalDebit;
    }

    public BigDecimal totalCredit() {
        return totalCredit;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }

    public Instant approvedAt() {
        return approvedAt;
    }

    public Instant cancelledAt() {
        return cancelledAt;
    }
}
