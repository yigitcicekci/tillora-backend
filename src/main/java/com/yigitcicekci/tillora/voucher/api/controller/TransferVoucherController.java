package com.yigitcicekci.tillora.voucher.api.controller;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.voucher.api.request.CreateTransferVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.application.service.TransferVoucherService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/vouchers/transfers")
public class TransferVoucherController {

    private final TransferVoucherService transferVoucherService;

    public TransferVoucherController(TransferVoucherService transferVoucherService) {
        this.transferVoucherService = transferVoucherService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    ResponseEntity<VoucherResponse> create(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreateTransferVoucherRequest request
    ) {
        VoucherResponse response = transferVoucherService.create(
            principal.companyId(), principal.userId(), request
        );
        return ResponseEntity.created(URI.create("/api/v1/vouchers/" + response.id())).body(response);
    }
}
