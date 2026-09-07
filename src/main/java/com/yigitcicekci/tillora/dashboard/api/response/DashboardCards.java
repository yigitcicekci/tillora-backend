package com.yigitcicekci.tillora.dashboard.api.response;

import java.math.BigDecimal;

public record DashboardCards(
    BigDecimal totalReceivables,
    BigDecimal overdueReceivables,
    BigDecimal totalPayables,
    BigDecimal cashBalance,
    BigDecimal bankBalance,
    BigDecimal monthlySales,
    BigDecimal monthlyPurchases,
    BigDecimal collections,
    BigDecimal payments
) {
}
