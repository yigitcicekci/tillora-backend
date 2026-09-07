package com.yigitcicekci.tillora.platformadmin.api.controller;

import com.yigitcicekci.tillora.platformadmin.api.response.PlatformAdminSystemHealthResponse;
import com.yigitcicekci.tillora.platformadmin.application.service.PlatformAdminSystemHealthService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/system")
public class PlatformAdminSystemController {

    private final PlatformAdminSystemHealthService healthService;

    public PlatformAdminSystemController(PlatformAdminSystemHealthService healthService) {
        this.healthService = healthService;
    }

    @GetMapping("/health")
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    PlatformAdminSystemHealthResponse health() {
        return healthService.get();
    }
}
