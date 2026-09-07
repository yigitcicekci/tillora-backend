package com.yigitcicekci.tillora.chartofaccount.application.service;

import com.yigitcicekci.tillora.chartofaccount.domain.entity.AccountCodeSequence;
import com.yigitcicekci.tillora.chartofaccount.domain.entity.ChartOfAccount;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.SystemAccountKey;
import com.yigitcicekci.tillora.chartofaccount.domain.repository.AccountCodeSequenceRepository;
import com.yigitcicekci.tillora.chartofaccount.domain.repository.ChartOfAccountRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class LedgerAccountProvisioningService {

    private static final String DIRECT_GROUP_CODE = "DIRECT";
    private static final long DIRECT_ACCOUNT_LIMIT = 99;

    private final AccountCodeSequenceRepository accountCodeSequenceRepository;
    private final ChartOfAccountRepository chartOfAccountRepository;

    public LedgerAccountProvisioningService(
        AccountCodeSequenceRepository accountCodeSequenceRepository,
        ChartOfAccountRepository chartOfAccountRepository
    ) {
        this.accountCodeSequenceRepository = accountCodeSequenceRepository;
        this.chartOfAccountRepository = chartOfAccountRepository;
    }

    public LedgerAccountProvision createReceivableLedgerAccount(UUID companyId, String groupCode, String accountName) {
        return createLedgerAccount(companyId, SystemAccountKey.RECEIVABLES, groupCode, accountName);
    }

    public LedgerAccountProvision createPayableLedgerAccount(UUID companyId, String groupCode, String accountName) {
        return createLedgerAccount(companyId, SystemAccountKey.PAYABLES, groupCode, accountName);
    }

    public PostingAccountProvision createCashPostingAccount(UUID companyId, String accountName) {
        return createDirectPostingAccount(companyId, accountName, SystemAccountKey.CASH);
    }

    public PostingAccountProvision createBankPostingAccount(UUID companyId, String accountName) {
        return createDirectPostingAccount(companyId, accountName, SystemAccountKey.BANKS);
    }

    public void disableCashPostingAccount(UUID companyId, UUID chartOfAccountId) {
        disableDirectPostingAccount(companyId, chartOfAccountId, SystemAccountKey.CASH);
    }

    public void disableBankPostingAccount(UUID companyId, UUID chartOfAccountId) {
        disableDirectPostingAccount(companyId, chartOfAccountId, SystemAccountKey.BANKS);
    }

    public void deleteUnusedCurrentAccountPostingAccounts(UUID companyId, Collection<UUID> chartOfAccountIds) {
        if (chartOfAccountIds.isEmpty()) {
            return;
        }
        List<ChartOfAccount> accounts = chartOfAccountRepository.findAllForUpdate(companyId, chartOfAccountIds);
        if (accounts.size() != chartOfAccountIds.size()
            || accounts.stream().anyMatch(account -> account.level() != 3 || !account.postingAllowed())) {
            throw new BusinessException(
                "CURRENT_ACCOUNT_LEDGER_ACCOUNT_INVALID",
                "Current account ledger account is invalid.",
                HttpStatus.CONFLICT
            );
        }
        chartOfAccountRepository.deleteAll(accounts);
        chartOfAccountRepository.flush();
    }

    public void renameCurrentAccountPostingAccounts(UUID companyId, Collection<UUID> chartOfAccountIds, String name) {
        if (chartOfAccountRepository.renameAll(companyId, chartOfAccountIds, name) != chartOfAccountIds.size()) {
            throw new BusinessException(
                "CURRENT_ACCOUNT_LEDGER_ACCOUNT_INVALID",
                "Current account ledger account is invalid.",
                HttpStatus.CONFLICT
            );
        }
    }

    private PostingAccountProvision createDirectPostingAccount(
        UUID companyId,
        String accountName,
        SystemAccountKey systemAccountKey
    ) {
        ChartOfAccount parent = activeSystemAccount(companyId, systemAccountKey);
        AccountCodeSequence sequence = lockedSequence(companyId, parent.code(), DIRECT_GROUP_CODE);
        long next = sequence.nextValue();
        if (next > DIRECT_ACCOUNT_LIMIT) {
            throw new BusinessException(
                directAccountType(systemAccountKey) + "_ACCOUNT_CODE_EXHAUSTED",
                directAccountLabel(systemAccountKey) + " account code capacity is exhausted.",
                HttpStatus.CONFLICT
            );
        }
        String fullCode = parent.code() + "." + String.format("%02d", next);
        ChartOfAccount account = ChartOfAccount.postingAccount(
            companyId,
            fullCode,
            accountName,
            parent.id(),
            2,
            parent.category(),
            parent.nature()
        );
        ChartOfAccount savedAccount = chartOfAccountRepository.saveAndFlush(account);
        return new PostingAccountProvision(savedAccount.id(), fullCode);
    }

    private void disableDirectPostingAccount(
        UUID companyId,
        UUID chartOfAccountId,
        SystemAccountKey systemAccountKey
    ) {
        String accountType = directAccountType(systemAccountKey);
        String accountLabel = directAccountLabel(systemAccountKey);
        ChartOfAccount account = chartOfAccountRepository.findByIdAndCompanyIdAndActiveTrue(chartOfAccountId, companyId)
            .orElseThrow(() -> new BusinessException(
                accountType + "_POSTING_ACCOUNT_NOT_FOUND",
                accountLabel + " posting account not found."
            ));
        ChartOfAccount parent = chartOfAccountRepository.findByIdAndCompanyIdAndActiveTrue(account.parentId(), companyId)
            .orElseThrow(() -> new BusinessException(
                accountType + "_PARENT_ACCOUNT_NOT_FOUND",
                accountLabel + " parent account not found."
            ));
        if (!account.postingAllowed() || account.level() != 2 || parent.systemKey() != systemAccountKey) {
            throw new BusinessException(
                "INVALID_" + accountType + "_POSTING_ACCOUNT",
                "Chart account is not a " + accountLabel.toLowerCase() + " posting account."
            );
        }
        account.deactivate();
    }

    private LedgerAccountProvision createLedgerAccount(
        UUID companyId,
        SystemAccountKey systemAccountKey,
        String groupCode,
        String accountName
    ) {
        ChartOfAccount parent = activeSystemAccount(companyId, systemAccountKey);
        AccountCodeSequence sequence = lockedSequence(companyId, parent.code(), groupCode);
        long next = sequence.nextValue();
        String fullCode = parent.code() + "." + groupCode + "." + String.format("%06d", next);
        ChartOfAccount account = ChartOfAccount.ledgerAccount(
            companyId,
            fullCode,
            accountName,
            parent.id(),
            parent.category(),
            parent.nature()
        );
        ChartOfAccount savedAccount = chartOfAccountRepository.saveAndFlush(account);
        return new LedgerAccountProvision(savedAccount.id(), parent.code(), groupCode, next, fullCode);
    }

    private ChartOfAccount activeSystemAccount(UUID companyId, SystemAccountKey systemAccountKey) {
        return chartOfAccountRepository.findByCompanyIdAndSystemKeyAndActiveTrue(companyId, systemAccountKey)
            .orElseThrow(() -> new BusinessException("PARENT_ACCOUNT_NOT_FOUND", "Parent account not found."));
    }

    private AccountCodeSequence lockedSequence(UUID companyId, String mainAccountCode, String groupCode) {
        return accountCodeSequenceRepository
            .findByCompanyIdAndMainAccountCodeAndGroupCode(companyId, mainAccountCode, groupCode)
            .orElseThrow(() -> new BusinessException("ACCOUNT_CODE_SEQUENCE_NOT_FOUND", "Account code sequence not found."));
    }

    private String directAccountType(SystemAccountKey systemAccountKey) {
        return switch (systemAccountKey) {
            case CASH -> "CASH";
            case BANKS -> "BANK";
            default -> throw new IllegalArgumentException("Unsupported direct posting account type.");
        };
    }

    private String directAccountLabel(SystemAccountKey systemAccountKey) {
        return switch (systemAccountKey) {
            case CASH -> "Cash";
            case BANKS -> "Bank";
            default -> throw new IllegalArgumentException("Unsupported direct posting account type.");
        };
    }
}
