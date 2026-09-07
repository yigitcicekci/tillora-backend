package com.yigitcicekci.tillora.voucher.api.controller;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.voucher.api.request.CancelVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.CreateVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.UpdateVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.api.response.VoucherSummaryResponse;
import com.yigitcicekci.tillora.voucher.application.service.VoucherService;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vouchers")
public class VoucherController {

    private final VoucherService voucherService;

    public VoucherController(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    ResponseEntity<VoucherResponse> create(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreateVoucherRequest request
    ) {
        VoucherResponse response = voucherService.create(principal.companyId(), principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/vouchers/" + response.id())).body(response);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    VoucherResponse update(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateVoucherRequest request
    ) {
        return voucherService.update(principal.companyId(), principal.userId(), id, request);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    Page<VoucherSummaryResponse> list(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) VoucherType type,
        @RequestParam(required = false) VoucherStatus status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @PageableDefault(
            size = 20,
            sort = {"voucherDate", "id"},
            direction = Sort.Direction.DESC
        ) Pageable pageable
    ) {
        return voucherService.list(principal.companyId(), type, status, dateFrom, dateTo, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('VOUCHER_READ')")
    VoucherResponse get(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID id
    ) {
        return voucherService.get(principal.companyId(), id);
    }

    @PostMapping("/{id}/approve")
    @PreAuthorize("hasAuthority('VOUCHER_APPROVE')")
    VoucherResponse approve(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID id
    ) {
        return voucherService.approve(principal.companyId(), principal.userId(), id);
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAuthority('VOUCHER_CANCEL')")
    VoucherResponse cancel(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody CancelVoucherRequest request
    ) {
        return voucherService.cancel(principal.companyId(), principal.userId(), id, request.reason());
    }
}
