package com.yigitcicekci.tillora.notification.web.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record SendCurrentAccountStatementEmailRequest(
    @NotEmpty List<@NotBlank @Size(max = 254) String> to,
    @NotNull List<@NotBlank @Size(max = 254) String> cc,
    @NotBlank @Size(max = 200) String subject,
    @NotNull @Size(max = 5000) String message,
    @NotBlank @Size(max = 40_000_000) String pdfBase64
) {
}
