package com.yigitcicekci.tillora.product.domain.entity;

import com.yigitcicekci.tillora.product.domain.enumeration.ProductUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(length = 64)
    private String barcode;

    @Column(nullable = false, length = 180)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ProductUnit unit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal purchasePrice;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal vatRate;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Product() {
    }

    private Product(
        UUID companyId,
        String code,
        String barcode,
        String name,
        ProductUnit unit,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        BigDecimal vatRate
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.code = code;
        this.barcode = barcode;
        this.name = name;
        this.unit = unit;
        this.purchasePrice = purchasePrice;
        this.salePrice = salePrice;
        this.vatRate = vatRate;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Product create(
        UUID companyId,
        String code,
        String barcode,
        String name,
        ProductUnit unit,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        BigDecimal vatRate
    ) {
        return new Product(
            companyId,
            code,
            barcode,
            name,
            unit,
            purchasePrice,
            salePrice,
            vatRate
        );
    }

    public boolean update(
        String barcode,
        String name,
        ProductUnit unit,
        BigDecimal purchasePrice,
        BigDecimal salePrice,
        BigDecimal vatRate
    ) {
        if (Objects.equals(this.barcode, barcode)
            && this.name.equals(name)
            && this.unit == unit
            && this.purchasePrice.equals(purchasePrice)
            && this.salePrice.equals(salePrice)
            && this.vatRate.equals(vatRate)) {
            return false;
        }
        this.barcode = barcode;
        this.name = name;
        this.unit = unit;
        this.purchasePrice = purchasePrice;
        this.salePrice = salePrice;
        this.vatRate = vatRate;
        this.updatedAt = Instant.now();
        return true;
    }

    public boolean disable() {
        if (!active) {
            return false;
        }
        active = false;
        updatedAt = Instant.now();
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public String code() {
        return code;
    }

    public String barcode() {
        return barcode;
    }

    public String name() {
        return name;
    }

    public ProductUnit unit() {
        return unit;
    }

    public BigDecimal purchasePrice() {
        return purchasePrice;
    }

    public BigDecimal salePrice() {
        return salePrice;
    }

    public BigDecimal vatRate() {
        return vatRate;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
