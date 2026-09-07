package com.yigitcicekci.tillora.finance.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.finance.api.request.CreateCashAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.CashAccountResponse;
import com.yigitcicekci.tillora.finance.application.service.CashAccountService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class CashAccountControllerTest {

    private final CashAccountService service = mock(CashAccountService.class);
    private final CashAccountController controller = new CashAccountController(service);

    @Test
    void usesAuthenticatedTenantAndActorForCashAccountOperations() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID cashAccountId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            actorId,
            companyId,
            "accounting",
            Set.of("CASH_ACCOUNT_READ", "CASH_ACCOUNT_CREATE", "CASH_ACCOUNT_DISABLE")
        );
        CreateCashAccountRequest request = new CreateCashAccountRequest("Main Cash", null);
        Instant now = Instant.now();
        CashAccountResponse response = new CashAccountResponse(
            cashAccountId,
            "Main Cash",
            "100.01",
            "TRY",
            true,
            now,
            now
        );
        when(service.create(companyId, actorId, request)).thenReturn(response);

        var created = controller.create(principal, request);
        controller.list(principal, true, PageRequest.of(0, 20));
        controller.get(principal, cashAccountId);
        controller.disable(principal, cashAccountId);

        assertThat(created.getHeaders().getLocation())
            .isEqualTo(URI.create("/api/v1/cash-accounts/" + cashAccountId));
        verify(service).create(companyId, actorId, request);
        verify(service).list(companyId, true, PageRequest.of(0, 20));
        verify(service).get(companyId, cashAccountId);
        verify(service).disable(companyId, actorId, cashAccountId);
    }
}
