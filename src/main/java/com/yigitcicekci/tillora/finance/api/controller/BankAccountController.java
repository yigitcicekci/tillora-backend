package com.yigitcicekci.tillora.finance.api.controller;

import com.yigitcicekci.tillora.finance.api.request.CreateBankAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.BankAccountResponse;
import com.yigitcicekci.tillora.finance.application.service.BankAccountService;
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
@RequestMapping("/api/v1/bank-accounts")
public class BankAccountController {

    private final BankAccountService bankAccountService;

    public BankAccountController(BankAccountService bankAccountService) {
        this.bankAccountService = bankAccountService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('BANK_ACCOUNT_CREATE')")
    ResponseEntity<BankAccountResponse> create(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreateBankAccountRequest request
    ) {
        BankAccountResponse response = bankAccountService.create(principal.companyId(), principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/bank-accounts/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('BANK_ACCOUNT_READ')")
    Page<BankAccountResponse> list(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) Boolean active,
        Pageable pageable
    ) {
        return bankAccountService.list(principal.companyId(), active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('BANK_ACCOUNT_READ')")
    BankAccountResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return bankAccountService.get(principal.companyId(), id);
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('BANK_ACCOUNT_DISABLE')")
    BankAccountResponse disable(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return bankAccountService.disable(principal.companyId(), principal.userId(), id);
    }
}
