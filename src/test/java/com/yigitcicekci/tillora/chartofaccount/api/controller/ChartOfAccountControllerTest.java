package com.yigitcicekci.tillora.chartofaccount.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.chartofaccount.application.service.ChartOfAccountService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class ChartOfAccountControllerTest {

    @Test
    void usesAuthenticatedCompanyForChartOfAccountOperations() {
        ChartOfAccountService service = mock(ChartOfAccountService.class);
        ChartOfAccountController controller = new ChartOfAccountController(service);
        UUID companyId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(UUID.randomUUID(), companyId, "admin", Set.of("CHART_OF_ACCOUNT_READ"));

        controller.list(principal, PageRequest.of(0, 20));
        controller.get(principal, accountId);

        verify(service).list(companyId, PageRequest.of(0, 20));
        verify(service).get(companyId, accountId);
    }
}
