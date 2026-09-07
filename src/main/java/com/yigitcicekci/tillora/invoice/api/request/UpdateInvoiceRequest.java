package com.yigitcicekci.tillora.invoice.api.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record UpdateInvoiceRequest(
    @NotNull UUID currentAccountId,
    LocalDate invoiceDate,
    LocalDate dueDate,
    @Size(min = 3, max = 3) String currency,
    @NotEmpty @Size(max = 100) List<@Valid InvoiceLineRequest> lines
) {
}
