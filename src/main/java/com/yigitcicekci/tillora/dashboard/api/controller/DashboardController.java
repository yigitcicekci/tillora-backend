package com.yigitcicekci.tillora.dashboard.api.controller;

import com.yigitcicekci.tillora.dashboard.api.response.DashboardSummary;
import com.yigitcicekci.tillora.dashboard.application.service.DashboardService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.time.YearMonth;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('DASHBOARD_VIEW')")
    DashboardSummary summary(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false)
        @DateTimeFormat(pattern = "yyyy-MM") YearMonth period
    ) {
        return dashboardService.summary(principal.companyId(), period);
    }
}
