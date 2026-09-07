package com.yigitcicekci.tillora.company.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.chartofaccount.application.service.DefaultAccountingSetupService;
import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.enumeration.CompanyStatus;
import com.yigitcicekci.tillora.company.domain.repository.CompanyRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.UserAccessSetupService;
import com.yigitcicekci.tillora.user.application.service.UserService;
import com.yigitcicekci.tillora.company.api.request.CreateCompanyRequest;
import com.yigitcicekci.tillora.company.api.response.CompanyResponse;
import java.util.Currency;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CompanyServiceTest {

    private static final String PROVISIONING_TOKEN = "test-company-provisioning-token-32-characters";

    @Mock
    private CompanyRepository companyRepository;

    @Mock
    private DefaultAccountingSetupService defaultAccountingSetupService;

    @Mock
    private UserAccessSetupService userAccessSetupService;

    @Mock
    private UserService userService;

    @Mock
    private AuditLogService auditLogService;

    private CompanyService companyService;

    @BeforeEach
    void setUp() {
        companyService = new CompanyService(
            companyRepository,
            defaultAccountingSetupService,
            userAccessSetupService,
            userService,
            auditLogService,
            new CompanyProvisioningTokenVerifier(PROVISIONING_TOKEN)
        );
    }

    @Test
    void returnsFinancialContextForActiveCompany() {
        UUID companyId = UUID.randomUUID();
        Company company = Company.create(
            "Tillora",
            "Tillora AŞ",
            "1234567890",
            null,
            null,
            null,
            null,
            Currency.getInstance("TRY"),
            "Europe/Istanbul"
        );
        when(companyRepository.findByIdAndStatus(companyId, CompanyStatus.ACTIVE)).thenReturn(Optional.of(company));

        CompanyFinancialContext result = companyService.financialContext(companyId);

        assertThat(result).isEqualTo(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
    }

    @Test
    void rejectsMissingOrInactiveCompany() {
        UUID companyId = UUID.randomUUID();
        when(companyRepository.findByIdAndStatus(companyId, CompanyStatus.ACTIVE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> companyService.financialContext(companyId))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("COMPANY_NOT_FOUND");
    }

    @Test
    void createsCompanyWithAccountingAccessAndInitialAdministrator() {
        UUID adminUserId = UUID.randomUUID();
        CreateCompanyRequest request = request();
        when(companyRepository.saveAndFlush(any(Company.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userService.createCompanyAdmin(
            any(),
            any(),
            any(),
            any(),
            any(),
            any()
        )).thenReturn(adminUserId);

        CompanyResponse response = companyService.create(request);

        assertThat(response.name()).isEqualTo("Acme");
        assertThat(response.currency()).isEqualTo("TRY");
        assertThat(response.timezone()).isEqualTo("Europe/Istanbul");
        verify(companyRepository).acquireCompanyCreationLock();
        verify(defaultAccountingSetupService).initializeForCompany(response.id());
        verify(userAccessSetupService).initializeForCompany(response.id());
        verify(userService).createCompanyAdmin(
            response.id(),
            "acme-admin",
            "admin@acme.example",
            "temporary-password",
            "Acme",
            "Admin"
        );
        verify(auditLogService).record(
            response.id(),
            null,
            com.yigitcicekci.tillora.audit.application.service.AuditAction.COMPANY_CREATE,
            "COMPANY",
            response.id()
        );
    }

    @Test
    void rejectsDuplicateCompanyTaxNumberBeforeInitialization() {
        CreateCompanyRequest request = request();
        when(companyRepository.existsByTaxNumber("1234567890")).thenReturn(true);

        assertThatThrownBy(() -> companyService.create(request))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("COMPANY_TAX_NUMBER_ALREADY_EXISTS");
    }

    @Test
    void rejectsInvalidCompanyTimezone() {
        CreateCompanyRequest request = request("Unknown/Timezone");

        assertThatThrownBy(() -> companyService.create(request))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("COMPANY_TIMEZONE_INVALID");
    }

    private CreateCompanyRequest request() {
        return request("Europe/Istanbul");
    }

    private CreateCompanyRequest request(String timezone) {
        return new CreateCompanyRequest(
            PROVISIONING_TOKEN,
            " Acme ",
            " Acme AŞ ",
            "1234567890",
            null,
            null,
            null,
            null,
            "try",
            timezone,
            "acme-admin",
            "admin@acme.example",
            "temporary-password",
            "Acme",
            "Admin"
        );
    }
}
