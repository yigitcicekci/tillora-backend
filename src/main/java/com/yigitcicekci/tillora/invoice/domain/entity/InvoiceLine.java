package com.yigitcicekci.tillora.invoice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_lines")
public class InvoiceLine {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID invoiceId;

    @Column(nullable = false)
    private int lineNumber;

    @Column(nullable = false)
    private UUID productId;

    @Column(nullable = false, length = 64)
    private String productCode;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 19, scale = 6)
    private BigDecimal quantity;

    @Column(nullable = false, length = 32)
    private String unit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal discountRate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal discountAmount;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal vatAmount;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal lineTotal;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal unitCost;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal costTotal;

    @Column(nullable = false)
    private Instant createdAt;

    protected InvoiceLine() {
    }

    private InvoiceLine(
        UUID companyId,
        UUID invoiceId,
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
        BigDecimal costTotal
    ) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.invoiceId = invoiceId;
        this.lineNumber = lineNumber;
        this.productId = productId;
        this.productCode = productCode;
        this.description = description;
        this.quantity = quantity;
        this.unit = unit;
        this.unitPrice = unitPrice;
        this.discountRate = discountRate;
        this.discountAmount = discountAmount;
        this.vatRate = vatRate;
        this.vatAmount = vatAmount;
        this.lineTotal = lineTotal;
        this.unitCost = unitCost;
        this.costTotal = costTotal;
        this.createdAt = Instant.now();
    }

    public static InvoiceLine create(
        UUID companyId,
        UUID invoiceId,
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
        BigDecimal costTotal
    ) {
        return new InvoiceLine(
            companyId,
            invoiceId,
            lineNumber,
            productId,
            productCode,
            description,
            quantity,
            unit,
            unitPrice,
            discountRate,
            discountAmount,
            vatRate,
            vatAmount,
            lineTotal,
            unitCost,
            costTotal
        );
    }

    public UUID id() {
        return id;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public UUID productId() {
        return productId;
    }

    public String productCode() {
        return productCode;
    }

    public String description() {
        return description;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public String unit() {
        return unit;
    }

    public BigDecimal unitPrice() {
        return unitPrice;
    }

    public BigDecimal discountRate() {
        return discountRate;
    }

    public BigDecimal discountAmount() {
        return discountAmount;
    }

    public BigDecimal vatRate() {
        return vatRate;
    }

    public BigDecimal vatAmount() {
        return vatAmount;
    }

    public BigDecimal lineTotal() {
        return lineTotal;
    }

    public BigDecimal unitCost() {
        return unitCost;
    }

    public BigDecimal costTotal() {
        return costTotal;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
