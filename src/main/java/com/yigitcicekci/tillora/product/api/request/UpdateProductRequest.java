package com.yigitcicekci.tillora.product.api.request;

import com.yigitcicekci.tillora.product.domain.enumeration.ProductUnit;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
    @Size(max = 64) String barcode,
    @NotBlank @Size(max = 180) String name,
    @NotNull ProductUnit unit,
    @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal purchasePrice,
    @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal salePrice,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal vatRate
) {
}
