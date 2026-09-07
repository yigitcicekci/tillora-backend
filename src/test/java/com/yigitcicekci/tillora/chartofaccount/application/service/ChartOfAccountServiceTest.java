package com.yigitcicekci.tillora.chartofaccount.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.chartofaccount.domain.entity.ChartOfAccount;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.AccountNature;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.ChartAccountCategory;
import com.yigitcicekci.tillora.chartofaccount.domain.repository.ChartOfAccountRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ChartOfAccountServiceTest {

    @Mock
    private ChartOfAccountRepository chartOfAccountRepository;

    private ChartOfAccountService chartOfAccountService;

    @BeforeEach
    void setUp() {
        chartOfAccountService = new ChartOfAccountService(chartOfAccountRepository);
    }

    @Test
    void findsDistinctActivePostingAccountsInOneBatch() {
        UUID companyId = UUID.randomUUID();
        ChartOfAccount first = postingAccount(companyId, "100.01", "Main Cash");
        ChartOfAccount second = postingAccount(companyId, "120.01.000001", "Customer");
        ArgumentCaptor<List<UUID>> accountIds = ArgumentCaptor.forClass(List.class);
        when(chartOfAccountRepository.findActivePostingAccounts(org.mockito.ArgumentMatchers.eq(companyId), accountIds.capture()))
            .thenReturn(List.of(first, second));

        List<PostingAccountReference> result = chartOfAccountService.findActivePostingAccounts(
            companyId,
            List.of(first.id(), first.id(), second.id())
        );

        assertThat(accountIds.getValue()).containsExactly(first.id(), second.id());
        assertThat(result).containsExactly(
            new PostingAccountReference(first.id(), first.code(), first.name()),
            new PostingAccountReference(second.id(), second.code(), second.name())
        );
        verify(chartOfAccountRepository).findActivePostingAccounts(companyId, accountIds.getValue());
    }

    @Test
    void returnsEmptyWithoutRepositoryCallForEmptyInput() {
        assertThat(chartOfAccountService.findActivePostingAccounts(UUID.randomUUID(), List.of())).isEmpty();

        verifyNoInteractions(chartOfAccountRepository);
    }

    private ChartOfAccount postingAccount(UUID companyId, String code, String name) {
        return ChartOfAccount.postingAccount(
            companyId,
            code,
            name,
            null,
            2,
            ChartAccountCategory.ASSET,
            AccountNature.DEBIT
        );
    }
}
