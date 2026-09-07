package com.yigitcicekci.tillora.invoice.domain.entity;

import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
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
@Table(name = "invoices")
public class Invoice {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 24)
    private String invoiceNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceType invoiceType;

    @Column(nullable = false)
    private UUID currentAccountId;

    @Column(nullable = false)
    private LocalDate invoiceDate;

    private LocalDate dueDate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 20, scale = 8)
    private BigDecimal exchangeRate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal subtotal;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal discountTotal;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal taxTotal;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal grandTotal;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal costTotal;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InvoiceStatus status;

    @Column(nullable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    private UUID accountingVoucherId;

    @Column(nullable = false)
    private UUID createdBy;

    private UUID approvedBy;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant approvedAt;

    private Instant cancelledAt;

    @Version
    private long version;

    protected Invoice() {
    }

    private Invoice(
        InvoiceType invoiceType,
        UUID companyId,
        String invoiceNumber,
        UUID currentAccountId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal costTotal,
        UUID idempotencyKey,
        String requestFingerprint,
        UUID createdBy
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.invoiceNumber = invoiceNumber;
        this.invoiceType = invoiceType;
        this.currentAccountId = currentAccountId;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.currency = currency;
        this.exchangeRate = new BigDecimal("1.00000000");
        this.subtotal = subtotal;
        this.discountTotal = discountTotal;
        this.taxTotal = taxTotal;
        this.grandTotal = grandTotal;
        this.costTotal = costTotal;
        this.status = InvoiceStatus.DRAFT;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Invoice createSales(
        UUID companyId,
        String invoiceNumber,
        UUID currentAccountId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal costTotal,
        UUID idempotencyKey,
        String requestFingerprint,
        UUID createdBy
    ) {
        return new Invoice(
            InvoiceType.SALES,
            companyId,
            invoiceNumber,
            currentAccountId,
            invoiceDate,
            dueDate,
            currency,
            subtotal,
            discountTotal,
            taxTotal,
            grandTotal,
            costTotal,
            idempotencyKey,
            requestFingerprint,
            createdBy
        );
    }

    public static Invoice createPurchase(
        UUID companyId,
        String invoiceNumber,
        UUID currentAccountId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal costTotal,
        UUID idempotencyKey,
        String requestFingerprint,
        UUID createdBy
    ) {
        return new Invoice(
            InvoiceType.PURCHASE,
            companyId,
            invoiceNumber,
            currentAccountId,
            invoiceDate,
            dueDate,
            currency,
            subtotal,
            discountTotal,
            taxTotal,
            grandTotal,
            costTotal,
            idempotencyKey,
            requestFingerprint,
            createdBy
        );
    }

    public void updateDraft(
        UUID currentAccountId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        String currency,
        BigDecimal subtotal,
        BigDecimal discountTotal,
        BigDecimal taxTotal,
        BigDecimal grandTotal,
        BigDecimal costTotal
    ) {
        this.currentAccountId = currentAccountId;
        this.invoiceDate = invoiceDate;
        this.dueDate = dueDate;
        this.currency = currency;
        this.subtotal = subtotal;
        this.discountTotal = discountTotal;
        this.taxTotal = taxTotal;
        this.grandTotal = grandTotal;
        this.costTotal = costTotal;
        this.updatedAt = Instant.now();
    }

    public void approve(UUID actorUserId, UUID accountingVoucherId) {
        Instant now = Instant.now();
        this.status = InvoiceStatus.APPROVED;
        this.approvedBy = actorUserId;
        this.accountingVoucherId = accountingVoucherId;
        this.approvedAt = now;
        this.updatedAt = now;
    }

    public UUID id() {
        return id;
    }

    public String invoiceNumber() {
        return invoiceNumber;
    }

    public InvoiceType invoiceType() {
        return invoiceType;
    }

    public UUID currentAccountId() {
        return currentAccountId;
    }

    public LocalDate invoiceDate() {
        return invoiceDate;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public String currency() {
        return currency;
    }

    public BigDecimal exchangeRate() {
        return exchangeRate;
    }

    public BigDecimal subtotal() {
        return subtotal;
    }

    public BigDecimal discountTotal() {
        return discountTotal;
    }

    public BigDecimal taxTotal() {
        return taxTotal;
    }

    public BigDecimal grandTotal() {
        return grandTotal;
    }

    public BigDecimal costTotal() {
        return costTotal;
    }

    public InvoiceStatus status() {
        return status;
    }

    public UUID idempotencyKey() {
        return idempotencyKey;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }

    public UUID accountingVoucherId() {
        return accountingVoucherId;
    }

    public UUID createdBy() {
        return createdBy;
    }

    public UUID approvedBy() {
        return approvedBy;
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
