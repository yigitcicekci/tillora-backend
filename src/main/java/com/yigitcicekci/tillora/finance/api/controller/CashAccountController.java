package com.yigitcicekci.tillora.finance.api.controller;

import com.yigitcicekci.tillora.finance.api.request.CreateCashAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.CashAccountResponse;
import com.yigitcicekci.tillora.finance.application.service.CashAccountService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cash-accounts")
public class CashAccountController {

    private final CashAccountService cashAccountService;

    public CashAccountController(CashAccountService cashAccountService) {
        this.cashAccountService = cashAccountService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CASH_ACCOUNT_CREATE')")
    ResponseEntity<CashAccountResponse> create(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreateCashAccountRequest request
    ) {
        CashAccountResponse response = cashAccountService.create(principal.companyId(), principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/cash-accounts/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CASH_ACCOUNT_READ')")
    Page<CashAccountResponse> list(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) Boolean active,
        Pageable pageable
    ) {
        return cashAccountService.list(principal.companyId(), active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CASH_ACCOUNT_READ')")
    CashAccountResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return cashAccountService.get(principal.companyId(), id);
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('CASH_ACCOUNT_DISABLE')")
    CashAccountResponse disable(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return cashAccountService.disable(principal.companyId(), principal.userId(), id);
    }
}
