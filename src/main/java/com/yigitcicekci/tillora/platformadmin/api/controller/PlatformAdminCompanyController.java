package com.yigitcicekci.tillora.platformadmin.api.controller;

import com.yigitcicekci.tillora.company.api.response.CompanyResponse;
import com.yigitcicekci.tillora.platformadmin.api.request.CreatePlatformAdminCompanyRequest;
import com.yigitcicekci.tillora.platformadmin.application.service.PlatformAdminCompanyService;
import com.yigitcicekci.tillora.platformadmin.infrastructure.security.PlatformAdminPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/companies")
public class PlatformAdminCompanyController {

    private final PlatformAdminCompanyService companyService;

    public PlatformAdminCompanyController(PlatformAdminCompanyService companyService) {
        this.companyService = companyService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    Page<CompanyResponse> list(@PageableDefault(size = 20) Pageable pageable) {
        return companyService.list(pageable);
    }

    @GetMapping("/{companyId}")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    CompanyResponse get(@PathVariable UUID companyId) {
        return companyService.get(companyId);
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    ResponseEntity<CompanyResponse> create(
        @AuthenticationPrincipal PlatformAdminPrincipal principal,
        @Valid @RequestBody CreatePlatformAdminCompanyRequest request
    ) {
        CompanyResponse response = companyService.create(principal.platformAdminId(), request);
        return ResponseEntity.created(URI.create("/internal/admin/companies/" + response.id())).body(response);
    }
}
