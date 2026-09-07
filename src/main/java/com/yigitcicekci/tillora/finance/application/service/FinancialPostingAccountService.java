package com.yigitcicekci.tillora.finance.application.service;

import com.yigitcicekci.tillora.finance.domain.entity.BankAccount;
import com.yigitcicekci.tillora.finance.domain.entity.CashAccount;
import com.yigitcicekci.tillora.finance.domain.repository.BankAccountRepository;
import com.yigitcicekci.tillora.finance.domain.repository.CashAccountRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FinancialPostingAccountService {

    private final CashAccountRepository cashAccountRepository;
    private final BankAccountRepository bankAccountRepository;

    public FinancialPostingAccountService(
        CashAccountRepository cashAccountRepository,
        BankAccountRepository bankAccountRepository
    ) {
        this.cashAccountRepository = cashAccountRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    @Transactional
    public FinancialPostingAccountReference findActiveCashPostingAccount(UUID companyId, UUID cashAccountId) {
        CashAccount account = cashAccountRepository.findActiveForPosting(cashAccountId, companyId)
            .orElseThrow(() -> new BusinessException(
                "CASH_ACCOUNT_NOT_AVAILABLE_FOR_POSTING",
                "An active cash account is required for posting.",
                HttpStatus.BAD_REQUEST
            ));
        return reference(account);
    }

    @Transactional
    public FinancialPostingAccountReference findActiveBankPostingAccount(UUID companyId, UUID bankAccountId) {
        BankAccount account = bankAccountRepository.findActiveForPosting(bankAccountId, companyId)
            .orElseThrow(() -> new BusinessException(
                "BANK_ACCOUNT_NOT_AVAILABLE_FOR_POSTING",
                "An active bank account is required for posting.",
                HttpStatus.BAD_REQUEST
            ));
        return new FinancialPostingAccountReference(
            account.id(),
            account.name(),
            account.chartOfAccountId(),
            account.accountCode(),
            account.currency()
        );
    }

    @Transactional
    public Set<UUID> lockCashAccountsForBalance(UUID companyId, Collection<UUID> chartOfAccountIds) {
        if (chartOfAccountIds == null || chartOfAccountIds.isEmpty()) {
            return Set.of();
        }
        List<UUID> distinctIds = new LinkedHashSet<>(chartOfAccountIds).stream().toList();
        return cashAccountRepository.findAllForBalanceCheck(companyId, distinctIds).stream()
            .map(CashAccount::chartOfAccountId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private FinancialPostingAccountReference reference(CashAccount account) {
        return new FinancialPostingAccountReference(
            account.id(),
            account.name(),
            account.chartOfAccountId(),
            account.accountCode(),
            account.currency()
        );
    }
}
