package com.yigitcicekci.tillora.chartofaccount.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.chartofaccount.domain.entity.AccountCodeSequence;
import com.yigitcicekci.tillora.chartofaccount.domain.entity.ChartOfAccount;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.AccountNature;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.ChartAccountCategory;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.SystemAccountKey;
import com.yigitcicekci.tillora.chartofaccount.domain.repository.AccountCodeSequenceRepository;
import com.yigitcicekci.tillora.chartofaccount.domain.repository.ChartOfAccountRepository;
import java.util.Optional;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class LedgerAccountProvisioningServiceTest {

    private final AccountCodeSequenceRepository sequenceRepository = mock(AccountCodeSequenceRepository.class);
    private final ChartOfAccountRepository chartOfAccountRepository = mock(ChartOfAccountRepository.class);
    private final LedgerAccountProvisioningService service = new LedgerAccountProvisioningService(
        sequenceRepository,
        chartOfAccountRepository
    );

    @Test
    void createsLevelTwoPostingAccountUnderCashSystemAccount() {
        UUID companyId = UUID.randomUUID();
        ChartOfAccount cashParent = ChartOfAccount.systemAccount(
            companyId,
            "100",
            "Cash",
            ChartAccountCategory.ASSET,
            AccountNature.DEBIT,
            SystemAccountKey.CASH
        );
        AccountCodeSequence sequence = AccountCodeSequence.create(companyId, "100", "DIRECT");
        when(chartOfAccountRepository.findByCompanyIdAndSystemKeyAndActiveTrue(companyId, SystemAccountKey.CASH))
            .thenReturn(Optional.of(cashParent));
        when(sequenceRepository.findByCompanyIdAndMainAccountCodeAndGroupCode(companyId, "100", "DIRECT"))
            .thenReturn(Optional.of(sequence));
        when(chartOfAccountRepository.saveAndFlush(any(ChartOfAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PostingAccountProvision provision = service.createCashPostingAccount(companyId, "Main Cash");

        ArgumentCaptor<ChartOfAccount> accountCaptor = ArgumentCaptor.forClass(ChartOfAccount.class);
        verify(chartOfAccountRepository).saveAndFlush(accountCaptor.capture());
        ChartOfAccount account = accountCaptor.getValue();
        assertThat(provision.chartOfAccountId()).isEqualTo(account.id());
        assertThat(provision.accountCode()).isEqualTo("100.01");
        assertThat(account.code()).isEqualTo("100.01");
        assertThat(account.name()).isEqualTo("Main Cash");
        assertThat(account.parentId()).isEqualTo(cashParent.id());
        assertThat(account.level()).isEqualTo(2);
        assertThat(account.category()).isEqualTo(ChartAccountCategory.ASSET);
        assertThat(account.nature()).isEqualTo(AccountNature.DEBIT);
        assertThat(account.postingAllowed()).isTrue();
        verify(chartOfAccountRepository)
            .findByCompanyIdAndSystemKeyAndActiveTrue(companyId, SystemAccountKey.CASH);
    }

    @Test
    void createsLevelTwoPostingAccountUnderBanksSystemAccount() {
        UUID companyId = UUID.randomUUID();
        ChartOfAccount banksParent = ChartOfAccount.systemAccount(
            companyId,
            "102",
            "Banks",
            ChartAccountCategory.ASSET,
            AccountNature.DEBIT,
            SystemAccountKey.BANKS
        );
        AccountCodeSequence sequence = AccountCodeSequence.create(companyId, "102", "DIRECT");
        when(chartOfAccountRepository.findByCompanyIdAndSystemKeyAndActiveTrue(companyId, SystemAccountKey.BANKS))
            .thenReturn(Optional.of(banksParent));
        when(sequenceRepository.findByCompanyIdAndMainAccountCodeAndGroupCode(companyId, "102", "DIRECT"))
            .thenReturn(Optional.of(sequence));
        when(chartOfAccountRepository.saveAndFlush(any(ChartOfAccount.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        PostingAccountProvision provision = service.createBankPostingAccount(companyId, "Main Bank");

        ArgumentCaptor<ChartOfAccount> accountCaptor = ArgumentCaptor.forClass(ChartOfAccount.class);
        verify(chartOfAccountRepository).saveAndFlush(accountCaptor.capture());
        ChartOfAccount account = accountCaptor.getValue();
        assertThat(provision.chartOfAccountId()).isEqualTo(account.id());
        assertThat(provision.accountCode()).isEqualTo("102.01");
        assertThat(account.code()).isEqualTo("102.01");
        assertThat(account.name()).isEqualTo("Main Bank");
        assertThat(account.parentId()).isEqualTo(banksParent.id());
        assertThat(account.level()).isEqualTo(2);
        assertThat(account.category()).isEqualTo(ChartAccountCategory.ASSET);
        assertThat(account.nature()).isEqualTo(AccountNature.DEBIT);
        assertThat(account.postingAllowed()).isTrue();
        verify(chartOfAccountRepository)
            .findByCompanyIdAndSystemKeyAndActiveTrue(companyId, SystemAccountKey.BANKS);
    }

    @Test
    void deletesOnlyLockedCurrentAccountPostingAccounts() {
        UUID companyId = UUID.randomUUID();
        ChartOfAccount account = ChartOfAccount.ledgerAccount(
            companyId,
            "120.01.000001",
            "Customer",
            UUID.randomUUID(),
            ChartAccountCategory.ASSET,
            AccountNature.DEBIT
        );
        when(chartOfAccountRepository.findAllForUpdate(companyId, List.of(account.id())))
            .thenReturn(List.of(account));

        service.deleteUnusedCurrentAccountPostingAccounts(companyId, List.of(account.id()));

        verify(chartOfAccountRepository).deleteAll(List.of(account));
        verify(chartOfAccountRepository).flush();
    }
}
