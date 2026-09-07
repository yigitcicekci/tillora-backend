package com.yigitcicekci.tillora.dashboard.api.response;

import java.time.Instant;
import java.time.YearMonth;

public record DashboardSummary(
    YearMonth period,
    String currency,
    DashboardCards cards,
    DashboardAlerts alerts,
    Instant generatedAt
) {
}
