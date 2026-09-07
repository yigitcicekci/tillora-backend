package com.yigitcicekci.tillora.voucher.api.controller;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.voucher.api.request.CreatePaymentVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.application.service.PaymentVoucherService;
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
@RequestMapping("/api/v1/vouchers/payments")
public class PaymentVoucherController {

    private final PaymentVoucherService paymentVoucherService;

    public PaymentVoucherController(PaymentVoucherService paymentVoucherService) {
        this.paymentVoucherService = paymentVoucherService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    ResponseEntity<VoucherResponse> create(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreatePaymentVoucherRequest request
    ) {
        VoucherResponse response = paymentVoucherService.create(
            principal.companyId(),
            principal.userId(),
            request
        );
        return ResponseEntity.created(URI.create("/api/v1/vouchers/" + response.id())).body(response);
    }
}
