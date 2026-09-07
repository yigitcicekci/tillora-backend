package com.yigitcicekci.tillora.platformadmin.api.controller;

import com.yigitcicekci.tillora.audit.api.response.PlatformAdminAuditLogResponse;
import com.yigitcicekci.tillora.audit.application.service.PlatformAdminAuditQueryService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/admin/audit")
public class PlatformAdminAuditController {

    private final PlatformAdminAuditQueryService queryService;

    public PlatformAdminAuditController(PlatformAdminAuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PLATFORM_ADMIN')")
    Page<PlatformAdminAuditLogResponse> findAll(
        @RequestParam(required = false) UUID platformAdminId,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String entityType,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return queryService.findAll(platformAdminId, dateFrom, dateTo, action, entityType, pageable);
    }
}
