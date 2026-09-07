package com.yigitcicekci.tillora.platformadmin.application.service;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.company.api.response.CompanyResponse;
import com.yigitcicekci.tillora.company.application.service.CompanyProvisioningCommand;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.platformadmin.api.request.CreatePlatformAdminCompanyRequest;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminCompanyService {

    private static final int MAX_PAGE_SIZE = 100;

    private final CompanyService companyService;
    private final AuditLogService auditLogService;

    public PlatformAdminCompanyService(CompanyService companyService, AuditLogService auditLogService) {
        this.companyService = companyService;
        this.auditLogService = auditLogService;
    }

    public Page<CompanyResponse> list(Pageable pageable) {
        validate(pageable);
        return companyService.listForPlatformAdmin(pageable);
    }

    public CompanyResponse get(UUID companyId) {
        return companyService.getForPlatformAdmin(companyId);
    }

    public CompanyResponse create(UUID platformAdminId, CreatePlatformAdminCompanyRequest request) {
        try {
            return companyService.createForPlatformAdmin(toCommand(request), platformAdminId);
        } catch (RuntimeException exception) {
            auditLogService.recordPlatformAdminRequiresNew(
                null,
                platformAdminId,
                AuditAction.COMPANY_CREATE,
                "COMPANY",
                null,
                "FAILURE"
            );
            throw exception;
        }
    }

    private CompanyProvisioningCommand toCommand(CreatePlatformAdminCompanyRequest request) {
        return new CompanyProvisioningCommand(
            request.name(),
            request.legalName(),
            request.taxNumber(),
            request.taxOffice(),
            request.address(),
            request.phone(),
            request.email(),
            request.currency(),
            request.timezone(),
            request.adminUsername(),
            request.adminEmail(),
            request.adminTemporaryPassword(),
            request.adminFirstName(),
            request.adminLastName()
        );
    }

    private void validate(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged() || pageable.getPageNumber() < 0
            || pageable.getPageSize() < 1 || pageable.getPageSize() > MAX_PAGE_SIZE) {
            throw new BusinessException(
                "COMPANY_PAGE_INVALID",
                "Company page size must be between 1 and 100.",
                HttpStatus.BAD_REQUEST
            );
        }
    }
}
