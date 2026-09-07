package com.yigitcicekci.tillora.company.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.company.api.request.CreateCompanyRequest;
import com.yigitcicekci.tillora.company.api.response.CompanyResponse;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.company.domain.enumeration.CompanyStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CompanyControllerTest {

    private final CompanyService companyService = mock(CompanyService.class);
    private final CompanyController controller = new CompanyController(companyService);

    @Test
    void createsCompanyUsingProvisioningTokenRequest() {
        CreateCompanyRequest request = request();
        CompanyResponse response = new CompanyResponse(
            UUID.randomUUID(),
            "Acme",
            "Acme AŞ",
            "1234567890",
            "Kadıköy",
            "İstanbul",
            "+90 212 000 00 00",
            "info@acme.example",
            "TRY",
            "Europe/Istanbul",
            CompanyStatus.ACTIVE,
            Instant.now(),
            Instant.now()
        );
        when(companyService.create(request)).thenReturn(response);

        assertThat(controller.create(request)).isSameAs(response);
        verify(companyService).create(request);
    }

    private CreateCompanyRequest request() {
        return new CreateCompanyRequest(
            "test-company-provisioning-token-32-characters",
            "Acme",
            "Acme AŞ",
            "1234567890",
            "Kadıköy",
            "İstanbul",
            "+90 212 000 00 00",
            "info@acme.example",
            "TRY",
            "Europe/Istanbul",
            "acme-admin",
            "admin@acme.example",
            "temporary-password",
            "Acme",
            "Admin"
        );
    }
}
