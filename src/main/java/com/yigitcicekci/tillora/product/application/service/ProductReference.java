package com.yigitcicekci.tillora.product.application.service;

import java.math.BigDecimal;
import java.util.UUID;

public record ProductReference(
    UUID id,
    String code,
    String name,
    String unit,
    BigDecimal purchasePrice,
    BigDecimal vatRate
) {
}
