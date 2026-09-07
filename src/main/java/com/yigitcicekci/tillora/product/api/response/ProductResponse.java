package com.yigitcicekci.tillora.product.api.response;

import com.yigitcicekci.tillora.product.domain.entity.Product;
import com.yigitcicekci.tillora.product.domain.enumeration.ProductUnit;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ProductResponse(
    UUID id,
    String code,
    String barcode,
    String name,
    ProductUnit unit,
    BigDecimal purchasePrice,
    BigDecimal salePrice,
    BigDecimal vatRate,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
            product.id(),
            product.code(),
            product.barcode(),
            product.name(),
            product.unit(),
            product.purchasePrice(),
            product.salePrice(),
            product.vatRate(),
            product.active(),
            product.createdAt(),
            product.updatedAt()
        );
    }
}
