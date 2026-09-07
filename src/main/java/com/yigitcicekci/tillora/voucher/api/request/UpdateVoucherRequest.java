package com.yigitcicekci.tillora.voucher.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record UpdateVoucherRequest(
    LocalDate voucherDate,
    @Size(max = 500) String movementNote,
    @Size(max = 80) String documentNumber,
    @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3 letter ISO currency code") String currency,
    @NotNull @Size(min = 2, max = 100) List<@Valid VoucherLineRequest> lines
) {
}
