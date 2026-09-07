package com.yigitcicekci.tillora.currentaccount.api.controller;

import com.yigitcicekci.tillora.currentaccount.api.request.CreateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.request.UpdateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.response.CurrentAccountResponse;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
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
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/current-accounts")
public class CurrentAccountController {

    private final CurrentAccountService currentAccountService;

    public CurrentAccountController(CurrentAccountService currentAccountService) {
        this.currentAccountService = currentAccountService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CURRENT_ACCOUNT_CREATE')")
    ResponseEntity<CurrentAccountResponse> create(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreateCurrentAccountRequest request
    ) {
        CurrentAccountResponse response = currentAccountService.create(principal.companyId(), principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/current-accounts/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('CURRENT_ACCOUNT_READ')")
    Page<CurrentAccountResponse> list(@AuthenticationPrincipal AuthenticatedPrincipal principal, Pageable pageable) {
        return currentAccountService.list(principal.companyId(), pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('CURRENT_ACCOUNT_READ')")
    CurrentAccountResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return currentAccountService.get(principal.companyId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('CURRENT_ACCOUNT_UPDATE')")
    CurrentAccountResponse update(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateCurrentAccountRequest request
    ) {
        return currentAccountService.update(principal.companyId(), principal.userId(), id, request);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('CURRENT_ACCOUNT_UPDATE')")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        currentAccountService.delete(principal.companyId(), principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
