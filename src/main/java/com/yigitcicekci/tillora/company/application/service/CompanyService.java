package com.yigitcicekci.tillora.company.application.service;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.company.api.request.CreateCompanyRequest;
import com.yigitcicekci.tillora.company.api.response.CompanyResponse;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.enumeration.CompanyStatus;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import com.yigitcicekci.tillora.user.application.service.UserService;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompanyService {

    private final CompanyRepository companyRepository;
    private final DefaultAccountingSetupService defaultAccountingSetupService;
    private final UserAccessSetupService userAccessSetupService;
    private final UserService userService;
    private final AuditLogService auditLogService;
    private final CompanyProvisioningTokenVerifier provisioningTokenVerifier;

    public CompanyService(
        CompanyRepository companyRepository,
        DefaultAccountingSetupService defaultAccountingSetupService,
        UserAccessSetupService userAccessSetupService,
        UserService userService,
        AuditLogService auditLogService,
        CompanyProvisioningTokenVerifier provisioningTokenVerifier
    ) {
        this.companyRepository = companyRepository;
        this.defaultAccountingSetupService = defaultAccountingSetupService;
        this.userAccessSetupService = userAccessSetupService;
        this.userService = userService;
        this.auditLogService = auditLogService;
        this.provisioningTokenVerifier = provisioningTokenVerifier;
    }

    @Transactional
    public void bootstrap(BootstrapCompanyCommand command) {
        companyRepository.acquireBootstrapLock();
        if (companyRepository.count() > 0) {
            return;
        }
        validateBootstrap(command);
        if (companyRepository.existsByTaxNumber(command.companyTaxNumber())) {
            return;
        }
        Currency currency = Currency.getInstance(command.currency().trim().toUpperCase(Locale.ROOT));
        String timezone = command.timezone().trim();
        ZoneId.of(timezone);
        Company company = companyRepository.saveAndFlush(Company.create(
            command.companyName().trim(),
            command.companyLegalName().trim(),
            command.companyTaxNumber().trim(),
            null,
            null,
            null,
            null,
            currency,
            timezone
        ));
        defaultAccountingSetupService.initializeForCompany(company.id());
        userAccessSetupService.initializeForCompany(company.id());
        UUID adminUserId = userService.createAppOwner(
            company.id(),
            command.adminUsername(),
            command.adminEmail(),
            command.adminPassword(),
            command.adminFirstName(),
            command.adminLastName()
        );
        auditLogService.record(company.id(), adminUserId, AuditAction.COMPANY_CREATE, "COMPANY", company.id());
    }

    @Transactional
    public CompanyResponse create(CreateCompanyRequest request) {
        provisioningTokenVerifier.verify(request.provisioningToken());
        return create(new CompanyProvisioningCommand(
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
        ), null);
    }

    @Transactional
    public CompanyResponse createForPlatformAdmin(CompanyProvisioningCommand command, UUID platformAdminId) {
        return create(command, platformAdminId);
    }

    private CompanyResponse create(CompanyProvisioningCommand command, UUID platformAdminId) {
        companyRepository.acquireCompanyCreationLock();
        String taxNumber = command.taxNumber().trim();
        if (companyRepository.existsByTaxNumber(taxNumber)) {
            throw new BusinessException("COMPANY_TAX_NUMBER_ALREADY_EXISTS", "Company tax number already exists.", HttpStatus.CONFLICT);
        }
        Currency currency = currency(command.currency());
        String timezone = timezone(command.timezone());
        Company company = companyRepository.saveAndFlush(Company.create(
            command.name().trim(),
            command.legalName().trim(),
            taxNumber,
            nullable(command.taxOffice()),
            nullable(command.address()),
            nullable(command.phone()),
            nullable(command.email()),
            currency,
            timezone
        ));
        defaultAccountingSetupService.initializeForCompany(company.id());
        userAccessSetupService.initializeForCompany(company.id());
        userService.createCompanyAdmin(
            company.id(),
            command.adminUsername(),
            command.adminEmail(),
            command.adminTemporaryPassword(),
            command.adminFirstName(),
            command.adminLastName()
        );
        if (platformAdminId == null) {
            auditLogService.record(company.id(), null, AuditAction.COMPANY_CREATE, "COMPANY", company.id());
        } else {
            auditLogService.recordPlatformAdmin(
                company.id(),
                platformAdminId,
                AuditAction.COMPANY_CREATE,
                "COMPANY",
                company.id(),
                "SUCCESS"
            );
        }
        return CompanyResponse.from(company);
    }

    @Transactional(readOnly = true)
    public Page<CompanyResponse> listForPlatformAdmin(Pageable pageable) {
        return companyRepository.findAll(pageable).map(CompanyResponse::from);
    }

    @Transactional(readOnly = true)
    public CompanyResponse getForPlatformAdmin(UUID id) {
        return companyRepository.findById(id)
            .map(CompanyResponse::from)
            .orElseThrow(() -> new BusinessException("COMPANY_NOT_FOUND", "Company not found.", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public CompanyResponse get(UUID id) {
        return companyRepository.findByIdAndStatus(id, CompanyStatus.ACTIVE)
            .map(CompanyResponse::from)
            .orElseThrow(() -> new BusinessException("COMPANY_NOT_FOUND", "Company not found.", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public String currency(UUID id) {
        return companyRepository.findByIdAndStatus(id, CompanyStatus.ACTIVE)
            .map(Company::currency)
            .orElseThrow(() -> new BusinessException("COMPANY_NOT_FOUND", "Company not found.", HttpStatus.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public String name(UUID id) {
        return companyRepository.findByIdAndStatus(id, CompanyStatus.ACTIVE)
            .map(Company::name)
            .orElseThrow(() -> new BusinessException(
                "COMPANY_NOT_FOUND",
                "Company not found.",
                HttpStatus.NOT_FOUND
            ));
    }

    @Transactional(readOnly = true)
    public CompanyFinancialContext financialContext(UUID id) {
        return companyRepository.findByIdAndStatus(id, CompanyStatus.ACTIVE)
            .map(company -> new CompanyFinancialContext(company.currency(), company.timezone()))
            .orElseThrow(() -> new BusinessException("COMPANY_NOT_FOUND", "Company not found.", HttpStatus.NOT_FOUND));
    }

    private void validateBootstrap(BootstrapCompanyCommand command) {
        if (blank(command.companyName()) || blank(command.companyLegalName()) || blank(command.companyTaxNumber())
            || blank(command.currency()) || blank(command.timezone()) || blank(command.adminUsername())
            || blank(command.adminEmail()) || blank(command.adminPassword()) || blank(command.adminFirstName())
            || blank(command.adminLastName())) {
            throw new BusinessException("BOOTSTRAP_CONFIGURATION_INVALID", "Bootstrap configuration is incomplete.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
        if (!command.companyTaxNumber().matches("\\d{10,11}") || command.adminPassword().length() < 10) {
            throw new BusinessException("BOOTSTRAP_CONFIGURATION_INVALID", "Bootstrap configuration is invalid.", HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private Currency currency(String value) {
        try {
            return Currency.getInstance(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("COMPANY_CURRENCY_INVALID", "Company currency is invalid.");
        }
    }

    private String timezone(String value) {
        String timezone = value.trim();
        try {
            ZoneId.of(timezone);
            return timezone;
        } catch (DateTimeException exception) {
            throw new BusinessException("COMPANY_TIMEZONE_INVALID", "Company timezone is invalid.");
        }
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
