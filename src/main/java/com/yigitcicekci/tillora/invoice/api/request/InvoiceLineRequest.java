package com.yigitcicekci.tillora.invoice.api.request;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceLineRequest(
    @NotNull UUID productId,
    @Size(max = 500) String description,
    @NotNull @DecimalMin("0.000001") @Digits(integer = 13, fraction = 6) BigDecimal quantity,
    @NotNull @DecimalMin("0") @Digits(integer = 15, fraction = 4) BigDecimal unitPrice,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal discountRate,
    @NotNull @DecimalMin("0") @DecimalMax("100") @Digits(integer = 3, fraction = 2) BigDecimal vatRate
) {
}
