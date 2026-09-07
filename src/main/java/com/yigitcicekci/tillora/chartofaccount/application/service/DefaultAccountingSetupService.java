package com.yigitcicekci.tillora.chartofaccount.application.service;

import com.yigitcicekci.tillora.chartofaccount.domain.entity.AccountCodeSequence;
import com.yigitcicekci.tillora.chartofaccount.domain.entity.ChartOfAccount;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.AccountNature;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.ChartAccountCategory;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.SystemAccountKey;
import com.yigitcicekci.tillora.chartofaccount.domain.repository.AccountCodeSequenceRepository;
import com.yigitcicekci.tillora.chartofaccount.domain.repository.ChartOfAccountRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DefaultAccountingSetupService {

    private final ChartOfAccountRepository chartOfAccountRepository;
    private final AccountCodeSequenceRepository accountCodeSequenceRepository;

    public DefaultAccountingSetupService(
        ChartOfAccountRepository chartOfAccountRepository,
        AccountCodeSequenceRepository accountCodeSequenceRepository
    ) {
        this.chartOfAccountRepository = chartOfAccountRepository;
        this.accountCodeSequenceRepository = accountCodeSequenceRepository;
    }

    public void initializeForCompany(UUID companyId) {
        if (chartOfAccountRepository.existsByCompanyId(companyId)) {
            throw new BusinessException("COMPANY_ACCOUNTING_ALREADY_INITIALIZED", "Company accounting setup already exists.");
        }
        chartOfAccountRepository.saveAll(defaultAccounts(companyId));
        accountCodeSequenceRepository.saveAll(defaultSequences(companyId));
    }

    private List<ChartOfAccount> defaultAccounts(UUID companyId) {
        return List.of(
            account(companyId, "100", "Cash", ChartAccountCategory.ASSET, AccountNature.DEBIT, SystemAccountKey.CASH),
            account(companyId, "102", "Banks", ChartAccountCategory.ASSET, AccountNature.DEBIT, SystemAccountKey.BANKS),
            account(companyId, "120", "Receivables", ChartAccountCategory.ASSET, AccountNature.DEBIT, SystemAccountKey.RECEIVABLES),
            account(companyId, "153", "Trade Goods", ChartAccountCategory.ASSET, AccountNature.DEBIT, SystemAccountKey.TRADE_GOODS),
            account(companyId, "191", "Deductible VAT", ChartAccountCategory.ASSET, AccountNature.DEBIT, SystemAccountKey.DEDUCTIBLE_VAT),
            account(companyId, "320", "Payables", ChartAccountCategory.LIABILITY, AccountNature.CREDIT, SystemAccountKey.PAYABLES),
            account(companyId, "391", "Calculated VAT", ChartAccountCategory.LIABILITY, AccountNature.CREDIT, SystemAccountKey.CALCULATED_VAT),
            account(companyId, "600", "Domestic Sales", ChartAccountCategory.INCOME, AccountNature.CREDIT, SystemAccountKey.DOMESTIC_SALES),
            account(companyId, "610", "Sales Returns", ChartAccountCategory.INCOME, AccountNature.DEBIT, SystemAccountKey.SALES_RETURNS),
            account(companyId, "621", "Cost of Goods Sold", ChartAccountCategory.EXPENSE, AccountNature.DEBIT, SystemAccountKey.COST_OF_GOODS_SOLD)
        );
    }

    private List<AccountCodeSequence> defaultSequences(UUID companyId) {
        return List.of(
            AccountCodeSequence.create(companyId, "100", "DIRECT"),
            AccountCodeSequence.create(companyId, "102", "DIRECT"),
            AccountCodeSequence.create(companyId, "120", "01"),
            AccountCodeSequence.create(companyId, "120", "02"),
            AccountCodeSequence.create(companyId, "320", "01"),
            AccountCodeSequence.create(companyId, "320", "02")
        );
    }

    private ChartOfAccount account(
        UUID companyId,
        String code,
        String name,
        ChartAccountCategory category,
        AccountNature nature,
        SystemAccountKey systemAccountKey
    ) {
        return ChartOfAccount.systemAccount(companyId, code, name, category, nature, systemAccountKey);
    }
}
