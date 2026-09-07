package com.yigitcicekci.tillora.dashboard.api.response;

public record DashboardAlerts(
    long overdueCollections,
    long upcomingPayments
) {
}
