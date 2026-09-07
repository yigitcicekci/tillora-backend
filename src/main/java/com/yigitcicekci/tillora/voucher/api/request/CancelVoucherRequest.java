package com.yigitcicekci.tillora.voucher.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelVoucherRequest(
    @NotBlank @Size(max = 500) String reason
) {
}
