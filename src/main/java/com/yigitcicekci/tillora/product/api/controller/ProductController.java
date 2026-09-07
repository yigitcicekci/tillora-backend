package com.yigitcicekci.tillora.product.api.controller;

import com.yigitcicekci.tillora.product.api.request.CreateProductRequest;
import com.yigitcicekci.tillora.product.api.request.UpdateProductRequest;
import com.yigitcicekci.tillora.product.api.response.ProductResponse;
import com.yigitcicekci.tillora.product.application.service.ProductService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    ResponseEntity<ProductResponse> create(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @Valid @RequestBody CreateProductRequest request
    ) {
        ProductResponse response = productService.create(principal.companyId(), principal.userId(), request);
        return ResponseEntity.created(URI.create("/api/v1/products/" + response.id())).body(response);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    Page<ProductResponse> list(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) Boolean active,
        Pageable pageable
    ) {
        return productService.list(principal.companyId(), active, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_READ')")
    ProductResponse get(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return productService.get(principal.companyId(), id);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    ProductResponse update(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID id,
        @Valid @RequestBody UpdateProductRequest request
    ) {
        return productService.update(principal.companyId(), principal.userId(), id, request);
    }

    @PatchMapping("/{id}/disable")
    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    ProductResponse disable(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        return productService.disable(principal.companyId(), principal.userId(), id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('PRODUCT_MANAGE')")
    ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedPrincipal principal, @PathVariable UUID id) {
        productService.delete(principal.companyId(), principal.userId(), id);
        return ResponseEntity.noContent().build();
    }
}
