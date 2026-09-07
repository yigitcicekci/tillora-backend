package com.yigitcicekci.tillora.audit.api.controller;

import com.yigitcicekci.tillora.audit.api.response.AuditLogResponse;
import com.yigitcicekci.tillora.audit.application.service.AuditQueryService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/audit")
public class AuditController {

    private final AuditQueryService queryService;

    public AuditController(AuditQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('AUDIT_VIEW')")
    Page<AuditLogResponse> findAll(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @RequestParam(required = false) UUID userId,
        @RequestParam(required = false) String action,
        @RequestParam(required = false) String entityType,
        @PageableDefault(size = 20) Pageable pageable
    ) {
        return queryService.findAll(
            principal.companyId(), dateFrom, dateTo, userId, action, entityType, pageable
        );
    }
}
