package com.yigitcicekci.tillora.voucher.api.request;

import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record CreateVoucherRequest(
    @NotNull UUID idempotencyKey,
    @NotNull VoucherType voucherType,
    LocalDate voucherDate,
    @Size(max = 500) String movementNote,
    @Size(max = 80) String documentNumber,
    @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3 letter ISO currency code") String currency,
    @NotNull @Size(min = 2, max = 100) List<@Valid VoucherLineRequest> lines
) {
}
