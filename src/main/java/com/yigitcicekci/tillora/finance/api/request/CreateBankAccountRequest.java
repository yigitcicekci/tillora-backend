package com.yigitcicekci.tillora.finance.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateBankAccountRequest(
    @NotBlank @Size(max = 180) String name,
    @NotBlank @Size(max = 160) String branch,
    @NotBlank @Size(max = 48) String iban,
    @NotBlank @Size(max = 80) String accountNumber,
    @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3 letter ISO currency code") String currency
) {
}
