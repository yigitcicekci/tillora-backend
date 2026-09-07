package com.yigitcicekci.tillora.finance.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.finance.api.request.CreateBankAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.BankAccountResponse;
import com.yigitcicekci.tillora.finance.application.service.BankAccountService;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.net.URI;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class BankAccountControllerTest {

    private final BankAccountService service = mock(BankAccountService.class);
    private final BankAccountController controller = new BankAccountController(service);

    @Test
    void usesAuthenticatedTenantAndActorForBankAccountOperations() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID bankAccountId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            actorId,
            companyId,
            "accounting",
            Set.of("BANK_ACCOUNT_READ", "BANK_ACCOUNT_CREATE", "BANK_ACCOUNT_DISABLE")
        );
        CreateBankAccountRequest request = new CreateBankAccountRequest(
            "Main Bank",
            "Istanbul Branch",
            "TR330006100519786457841326",
            "12345678",
            null
        );
        Instant now = Instant.now();
        BankAccountResponse response = new BankAccountResponse(
            bankAccountId,
            "Main Bank",
            "Istanbul Branch",
            "TR330006100519786457841326",
            "12345678",
            "102.01",
            "TRY",
            true,
            now,
            now
        );
        when(service.create(companyId, actorId, request)).thenReturn(response);

        var created = controller.create(principal, request);
        controller.list(principal, true, PageRequest.of(0, 20));
        controller.get(principal, bankAccountId);
        controller.disable(principal, bankAccountId);

        assertThat(created.getHeaders().getLocation())
            .isEqualTo(URI.create("/api/v1/bank-accounts/" + bankAccountId));
        verify(service).create(companyId, actorId, request);
        verify(service).list(companyId, true, PageRequest.of(0, 20));
        verify(service).get(companyId, bankAccountId);
        verify(service).disable(companyId, actorId, bankAccountId);
    }
}
