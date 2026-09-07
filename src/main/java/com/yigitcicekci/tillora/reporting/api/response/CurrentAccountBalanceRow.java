package com.yigitcicekci.tillora.reporting.api.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CurrentAccountBalanceRow(
    UUID currentAccountId,
    String name,
    String relationshipType,
    BigDecimal balance,
    BigDecimal overdueAmount,
    LocalDate nearestDueDate
) {
}
