package com.yigitcicekci.tillora.voucher.api.controller;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.voucher.api.request.CreateCollectionVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.application.service.CollectionVoucherService;
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
@RequestMapping("/api/v1/vouchers/collections")
public class CollectionVoucherController {

    private final CollectionVoucherService collectionVoucherService;

    public CollectionVoucherController(CollectionVoucherService collectionVoucherService) {
        this.collectionVoucherService = collectionVoucherService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('VOUCHER_CREATE')")
    ResponseEntity<VoucherResponse> create(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreateCollectionVoucherRequest request
    ) {
        VoucherResponse response = collectionVoucherService.create(
            principal.companyId(),
            principal.userId(),
            request
        );
        return ResponseEntity.created(URI.create("/api/v1/vouchers/" + response.id())).body(response);
    }
}
