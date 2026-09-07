package com.yigitcicekci.tillora.dashboard.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.dashboard.api.response.DashboardAlerts;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardCards;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardSummary;
import com.yigitcicekci.tillora.dashboard.application.service.DashboardService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DashboardControllerTest {

    @Test
    void usesAuthenticatedCompany() {
        DashboardService service = mock(DashboardService.class);
        DashboardController controller = new DashboardController(service);
        UUID companyId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 7);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            UUID.randomUUID(),
            companyId,
            "dashboard",
            Set.of("DASHBOARD_VIEW")
        );
        BigDecimal zero = new BigDecimal("0.0000");
        DashboardSummary summary = new DashboardSummary(
            period,
            "TRY",
            new DashboardCards(zero, zero, zero, zero, zero, zero, zero, zero, zero),
            new DashboardAlerts(0, 0),
            Instant.now()
        );
        when(service.summary(companyId, period)).thenReturn(summary);

        assertThat(controller.summary(principal, period)).isEqualTo(summary);
        verify(service).summary(companyId, period);
    }
}
