package com.yigitcicekci.tillora.currentaccount.api.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.currentaccount.api.request.CreateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.request.UpdateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.response.CurrentAccountResponse;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.CurrentAccountStatus;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class CurrentAccountControllerTest {

    private final CurrentAccountService service = mock(CurrentAccountService.class);
    private final CurrentAccountController controller = new CurrentAccountController(service);

    @Test
    void usesAuthenticatedCompanyForCurrentAccountOperations() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(actorId, companyId, "admin", Set.of("CURRENT_ACCOUNT_CREATE"));
        CreateCurrentAccountRequest request = new CreateCurrentAccountRequest(
            "Customer",
            null,
            "1234567890",
            null,
            null,
            null,
            null,
            null,
            TradeType.RETAIL,
            RelationshipType.CUSTOMER
        );
        CurrentAccountResponse response = new CurrentAccountResponse(
            currentAccountId,
            companyId,
            "Customer",
            null,
            "1234567890",
            null,
            null,
            null,
            null,
            null,
            TradeType.RETAIL,
            RelationshipType.CUSTOMER,
            CurrentAccountStatus.ACTIVE,
            null,
            null,
            List.of()
        );
        when(service.create(companyId, actorId, request)).thenReturn(response);
        UpdateCurrentAccountRequest updateRequest = new UpdateCurrentAccountRequest(
            "Updated Customer", null, "1234567890", null, null, null, null, null, null, null, null, null, null
        );
        when(service.update(companyId, actorId, currentAccountId, updateRequest)).thenReturn(response);

        controller.create(principal, request);
        controller.list(principal, PageRequest.of(0, 20));
        controller.get(principal, currentAccountId);
        controller.update(principal, currentAccountId, updateRequest);
        var deleted = controller.delete(principal, currentAccountId);

        verify(service).create(companyId, actorId, request);
        verify(service).list(companyId, PageRequest.of(0, 20));
        verify(service).get(companyId, currentAccountId);
        verify(service).update(companyId, actorId, currentAccountId, updateRequest);
        verify(service).delete(companyId, actorId, currentAccountId);
        assertThat(deleted.getStatusCode().value()).isEqualTo(204);
    }
}
