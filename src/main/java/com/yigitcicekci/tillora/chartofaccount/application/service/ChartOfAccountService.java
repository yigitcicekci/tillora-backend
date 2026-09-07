package com.yigitcicekci.tillora.chartofaccount.application.service;

import com.yigitcicekci.tillora.chartofaccount.api.response.ChartOfAccountResponse;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.SystemAccountKey;
import com.yigitcicekci.tillora.chartofaccount.domain.repository.ChartOfAccountRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.Collection;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChartOfAccountService {

    private final ChartOfAccountRepository chartOfAccountRepository;

    public ChartOfAccountService(ChartOfAccountRepository chartOfAccountRepository) {
        this.chartOfAccountRepository = chartOfAccountRepository;
    }

    @Transactional(readOnly = true)
    public Page<ChartOfAccountResponse> list(UUID companyId, Pageable pageable) {
        return chartOfAccountRepository.findByCompanyIdAndActiveTrue(companyId, pageable)
            .map(ChartOfAccountResponse::from);
    }

    @Transactional(readOnly = true)
    public ChartOfAccountResponse get(UUID companyId, UUID id) {
        return chartOfAccountRepository.findByIdAndCompanyIdAndActiveTrue(id, companyId)
            .map(ChartOfAccountResponse::from)
            .orElseThrow(() -> new BusinessException("CHART_OF_ACCOUNT_NOT_FOUND", "Chart of account not found.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public List<PostingAccountReference> findActivePostingAccounts(UUID companyId, Collection<UUID> accountIds) {
        if (accountIds == null || accountIds.isEmpty()) {
            return List.of();
        }
        List<UUID> distinctIds = new LinkedHashSet<>(accountIds).stream()
            .filter(Objects::nonNull)
            .toList();
        if (distinctIds.isEmpty()) {
            return List.of();
        }
        return chartOfAccountRepository.findActivePostingAccounts(companyId, distinctIds).stream()
            .map(account -> new PostingAccountReference(account.id(), account.code(), account.name()))
            .toList();
    }

    @Transactional
    public SalesInvoicePostingAccounts findSalesInvoicePostingAccounts(UUID companyId) {
        List<SystemAccountKey> keys = List.of(
            SystemAccountKey.DOMESTIC_SALES,
            SystemAccountKey.CALCULATED_VAT,
            SystemAccountKey.TRADE_GOODS,
            SystemAccountKey.COST_OF_GOODS_SOLD
        );
        Map<SystemAccountKey, PostingAccountReference> accounts = new EnumMap<>(SystemAccountKey.class);
        chartOfAccountRepository.findActiveSystemAccounts(companyId, keys).forEach(account ->
            accounts.put(
                account.systemKey(),
                new PostingAccountReference(account.id(), account.code(), account.name())
            )
        );
        if (accounts.size() != keys.size()) {
            throw new BusinessException(
                "SALES_INVOICE_SYSTEM_ACCOUNTS_MISSING",
                "Sales invoice accounting accounts are incomplete.",
                HttpStatus.CONFLICT
            );
        }
        return new SalesInvoicePostingAccounts(
            accounts.get(SystemAccountKey.DOMESTIC_SALES),
            accounts.get(SystemAccountKey.CALCULATED_VAT),
            accounts.get(SystemAccountKey.TRADE_GOODS),
            accounts.get(SystemAccountKey.COST_OF_GOODS_SOLD)
        );
    }

    @Transactional
    public PurchaseInvoicePostingAccounts findPurchaseInvoicePostingAccounts(UUID companyId) {
        List<SystemAccountKey> keys = List.of(
            SystemAccountKey.TRADE_GOODS,
            SystemAccountKey.DEDUCTIBLE_VAT
        );
        Map<SystemAccountKey, PostingAccountReference> accounts = new EnumMap<>(SystemAccountKey.class);
        chartOfAccountRepository.findActiveSystemAccounts(companyId, keys).forEach(account ->
            accounts.put(
                account.systemKey(),
                new PostingAccountReference(account.id(), account.code(), account.name())
            )
        );
        if (accounts.size() != keys.size()) {
            throw new BusinessException(
                "PURCHASE_INVOICE_SYSTEM_ACCOUNTS_MISSING",
                "Purchase invoice accounting accounts are incomplete.",
                HttpStatus.CONFLICT
            );
        }
        return new PurchaseInvoicePostingAccounts(
            accounts.get(SystemAccountKey.TRADE_GOODS),
            accounts.get(SystemAccountKey.DEDUCTIBLE_VAT)
        );
    }
}
