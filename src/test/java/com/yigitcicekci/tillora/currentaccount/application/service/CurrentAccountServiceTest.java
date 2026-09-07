package com.yigitcicekci.tillora.currentaccount.application.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.chartofaccount.application.service.LedgerAccountProvision;
import com.yigitcicekci.tillora.chartofaccount.application.service.LedgerAccountProvisioningService;
import com.yigitcicekci.tillora.currentaccount.api.request.CreateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.request.UpdateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.response.CurrentAccountResponse;
import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccount;
import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccountLedgerAccount;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.LedgerRole;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import com.yigitcicekci.tillora.currentaccount.domain.repository.CurrentAccountLedgerAccountRepository;
import com.yigitcicekci.tillora.currentaccount.domain.repository.CurrentAccountRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CurrentAccountServiceTest {

    private final CurrentAccountRepository currentAccountRepository = mock(CurrentAccountRepository.class);
    private final CurrentAccountLedgerAccountRepository ledgerAccountRepository = mock(CurrentAccountLedgerAccountRepository.class);
    private final LedgerAccountProvisioningService ledgerAccountProvisioningService = mock(LedgerAccountProvisioningService.class);
    private final CurrentAccountMovementService currentAccountMovementService = mock(CurrentAccountMovementService.class);
    private final AuditLogService auditLogService = mock(AuditLogService.class);
    private final CurrentAccountService service = new CurrentAccountService(
        currentAccountRepository,
        ledgerAccountRepository,
        ledgerAccountProvisioningService,
        currentAccountMovementService,
        auditLogService
    );

    @Test
    void rejectsCaseInsensitiveDuplicateCustomerName() {
        UUID companyId = UUID.randomUUID();
        when(currentAccountRepository.existsByCompanyIdAndNameAndRelationshipTypes(
            companyId,
            "Acme",
            List.of(RelationshipType.CUSTOMER, RelationshipType.BOTH)
        )).thenReturn(true);

        assertThatThrownBy(() -> service.create(
            companyId,
            UUID.randomUUID(),
            request(" Acme ", RelationshipType.CUSTOMER)
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("CURRENT_ACCOUNT_NAME_ALREADY_EXISTS");

        verify(currentAccountRepository, never()).save(org.mockito.ArgumentMatchers.any(CurrentAccount.class));
        verifyNoInteractions(ledgerAccountRepository, ledgerAccountProvisioningService, auditLogService);
    }

    @Test
    void recordsOptionalOpeningDebitBalance() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        LedgerAccountProvision provision = new LedgerAccountProvision(
            UUID.randomUUID(),
            "120",
            "01",
            1,
            "120.01.000001"
        );
        when(currentAccountRepository.save(any(CurrentAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(ledgerAccountProvisioningService.createReceivableLedgerAccount(companyId, "01", "Opening Customer"))
            .thenReturn(provision);

        CurrentAccountResponse response = service.create(
            companyId,
            actorId,
            new CreateCurrentAccountRequest(
                "Opening Customer",
                null,
                "1234567890",
                null,
                null,
                null,
                null,
                null,
                TradeType.RETAIL,
                RelationshipType.CUSTOMER,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("125.5000"),
                null
            )
        );

        verify(currentAccountMovementService).recordOpeningBalance(
            eq(companyId),
            eq(response.id()),
            any(),
            eq(new BigDecimal("125.5000")),
            eq(new BigDecimal("0.0000"))
        );
    }

    @Test
    void rejectsBothOpeningDebitAndCreditBalances() {
        UUID companyId = UUID.randomUUID();
        when(currentAccountRepository.existsByCompanyIdAndNameAndRelationshipTypes(
            companyId,
            "Opening Customer",
            List.of(RelationshipType.CUSTOMER, RelationshipType.BOTH)
        )).thenReturn(false);

        assertThatThrownBy(() -> service.create(
            companyId,
            UUID.randomUUID(),
            new CreateCurrentAccountRequest(
                "Opening Customer",
                null,
                "1234567890",
                null,
                null,
                null,
                null,
                null,
                TradeType.RETAIL,
                RelationshipType.CUSTOMER,
                null,
                null,
                null,
                null,
                null,
                new BigDecimal("100"),
                new BigDecimal("50")
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("CURRENT_ACCOUNT_OPENING_BALANCE_INVALID");

        verify(currentAccountRepository, never()).save(any(CurrentAccount.class));
        verifyNoInteractions(currentAccountMovementService, ledgerAccountRepository, ledgerAccountProvisioningService);
    }

    @Test
    void deletesUnusedCurrentAccountAndItsPostingAccounts() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CurrentAccount currentAccount = CurrentAccount.create(
            companyId,
            "Unused Customer",
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
        when(currentAccountRepository.findForUpdate(currentAccount.id(), companyId))
            .thenReturn(Optional.of(currentAccount));
        when(ledgerAccountRepository.findByCurrentAccountIdIn(List.of(currentAccount.id())))
            .thenReturn(List.of());

        service.delete(companyId, actorId, currentAccount.id());

        verify(ledgerAccountProvisioningService).deleteUnusedCurrentAccountPostingAccounts(companyId, List.of());
        verify(currentAccountRepository).delete(currentAccount);
        verify(currentAccountRepository).flush();
        verify(auditLogService).record(
            companyId,
            actorId,
            AuditAction.CURRENT_ACCOUNT_DELETE,
            "CURRENT_ACCOUNT",
            currentAccount.id()
        );
    }

    @Test
    void updatesCurrentAccountAndItsPostingAccountName() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CurrentAccount currentAccount = account(companyId);
        CurrentAccountLedgerAccount ledgerAccount = CurrentAccountLedgerAccount.create(
            companyId,
            currentAccount.id(),
            LedgerRole.RECEIVABLE,
            UUID.randomUUID(),
            "120",
            "01",
            1,
            "120.01.000001"
        );
        when(currentAccountRepository.findForUpdate(currentAccount.id(), companyId)).thenReturn(Optional.of(currentAccount));
        when(ledgerAccountRepository.findByCurrentAccountIdIn(List.of(currentAccount.id()))).thenReturn(List.of(ledgerAccount));

        CurrentAccountResponse response = service.update(
            companyId,
            actorId,
            currentAccount.id(),
            updateRequest("Updated Customer", "5551234567")
        );

        assertThat(response.name()).isEqualTo("Updated Customer");
        assertThat(response.phone()).isEqualTo("5551234567");
        verify(ledgerAccountProvisioningService).renameCurrentAccountPostingAccounts(
            companyId,
            List.of(ledgerAccount.chartOfAccountId()),
            "Updated Customer"
        );
        verify(currentAccountRepository).flush();
        verify(auditLogService).record(
            companyId,
            actorId,
            AuditAction.CURRENT_ACCOUNT_UPDATE,
            "CURRENT_ACCOUNT",
            currentAccount.id()
        );
    }

    @Test
    void skipsWriteAndAuditWhenCurrentAccountIsUnchanged() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        CurrentAccount currentAccount = account(companyId);
        when(currentAccountRepository.findForUpdate(currentAccount.id(), companyId)).thenReturn(Optional.of(currentAccount));
        when(ledgerAccountRepository.findByCurrentAccountIdIn(List.of(currentAccount.id()))).thenReturn(List.of());

        service.update(companyId, actorId, currentAccount.id(), updateRequest("Customer", null));

        verify(currentAccountRepository, never()).flush();
        verifyNoInteractions(ledgerAccountProvisioningService, auditLogService);
    }

    private CurrentAccount account(UUID companyId) {
        return CurrentAccount.create(
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
            RelationshipType.CUSTOMER
        );
    }

    private UpdateCurrentAccountRequest updateRequest(String name, String phone) {
        return new UpdateCurrentAccountRequest(
            name,
            null,
            "1234567890",
            null,
            null,
            phone,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }

    private CreateCurrentAccountRequest request(String name, RelationshipType relationshipType) {
        return new CreateCurrentAccountRequest(
            name,
            null,
            "1234567890",
            null,
            null,
            null,
            null,
            null,
            TradeType.RETAIL,
            relationshipType
        );
    }
}
