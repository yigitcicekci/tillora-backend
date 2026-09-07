package com.yigitcicekci.tillora.finance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCashAccountRequest(
    @NotBlank @Size(max = 180) String name,
    @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3 letter ISO currency code") String currency
) {
}
