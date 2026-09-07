package com.yigitcicekci.tillora.chartofaccount.api.controller;

import com.yigitcicekci.tillora.chartofaccount.api.response.ChartOfAccountResponse;
import com.yigitcicekci.tillora.chartofaccount.application.service.ChartOfAccountService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chart-of-accounts")
public class ChartOfAccountController {

    private final ChartOfAccountService chartOfAccountService;

    public ChartOfAccountController(ChartOfAccountService chartOfAccountService) {
        this.chartOfAccountService = chartOfAccountService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CHART_OF_ACCOUNT_READ')")
    Page<ChartOfAccountResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal, Pageable pageable) {
        return chartOfAccountService.list(principal.companyId(), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CHART_OF_ACCOUNT_READ')")
    ChartOfAccountResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return chartOfAccountService.get(principal.companyId(), id);
    }
}
