package com.yigitcicekci.tillora.finance.application.service;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.chartofaccount.application.service.LedgerAccountProvisioningService;
import com.yigitcicekci.tillora.chartofaccount.application.service.PostingAccountProvision;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.finance.api.request.CreateCashAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.CashAccountResponse;
import com.yigitcicekci.tillora.finance.domain.entity.CashAccount;
import com.yigitcicekci.tillora.finance.domain.repository.CashAccountRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.Currency;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CashAccountService {

    private final CashAccountRepository cashAccountRepository;
    private final LedgerAccountProvisioningService ledgerAccountProvisioningService;
    private final CompanyService companyService;
    private final AuditLogService auditLogService;

    public CashAccountService(
        CashAccountRepository cashAccountRepository,
        LedgerAccountProvisioningService ledgerAccountProvisioningService,
        CompanyService companyService,
        AuditLogService auditLogService
    ) {
        this.cashAccountRepository = cashAccountRepository;
        this.ledgerAccountProvisioningService = ledgerAccountProvisioningService;
        this.companyService = companyService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CashAccountResponse create(UUID companyId, UUID actorUserId, CreateCashAccountRequest request) {
        String name = request.name().trim();
        if (cashAccountRepository.existsByCompanyIdAndNameIgnoreCase(companyId, name)) {
            throw new BusinessException(
                "CASH_ACCOUNT_NAME_ALREADY_EXISTS",
                "Cash account name already exists.",
                HttpStatus.CONFLICT
            );
        }
        String currency = resolveCurrency(companyId, request.currency());
        PostingAccountProvision provision = ledgerAccountProvisioningService.createCashPostingAccount(companyId, name);
        CashAccount cashAccount = cashAccountRepository.saveAndFlush(CashAccount.create(
            companyId,
            name,
            provision.chartOfAccountId(),
            provision.accountCode(),
            currency
        ));
        auditLogService.record(
            companyId,
            actorUserId,
            AuditAction.ACCOUNT_CODE_GENERATE,
            "CHART_OF_ACCOUNT",
            provision.chartOfAccountId()
        );
        auditLogService.record(
            companyId,
            actorUserId,
            AuditAction.CASH_ACCOUNT_CREATE,
            "CASH_ACCOUNT",
            cashAccount.id()
        );
        return CashAccountResponse.from(cashAccount);
    }

    @Transactional(readOnly = true)
    public Page<CashAccountResponse> list(UUID companyId, Boolean active, Pageable pageable) {
        Page<CashAccount> accounts = active == null
            ? cashAccountRepository.findByCompanyId(companyId, pageable)
            : cashAccountRepository.findByCompanyIdAndActive(companyId, active, pageable);
        return accounts.map(CashAccountResponse::from);
    }

    @Transactional(readOnly = true)
    public CashAccountResponse get(UUID companyId, UUID id) {
        return cashAccountRepository.findByIdAndCompanyId(id, companyId)
            .map(CashAccountResponse::from)
            .orElseThrow(() -> new BusinessException("CASH_ACCOUNT_NOT_FOUND", "Cash account not found.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public CashAccountResponse disable(UUID companyId, UUID actorUserId, UUID id) {
        CashAccount cashAccount = cashAccountRepository.findForUpdate(id, companyId)
            .orElseThrow(() -> new BusinessException("CASH_ACCOUNT_NOT_FOUND", "Cash account not found.", HttpStatus.NOT_FOUND));
        if (cashAccount.disable()) {
            ledgerAccountProvisioningService.disableCashPostingAccount(companyId, cashAccount.chartOfAccountId());
            auditLogService.record(
                companyId,
                actorUserId,
                AuditAction.CASH_ACCOUNT_DISABLE,
                "CASH_ACCOUNT",
                cashAccount.id()
            );
        }
        return CashAccountResponse.from(cashAccount);
    }

    private String resolveCurrency(UUID companyId, String requestedCurrency) {
        String value = requestedCurrency == null ? companyService.currency(companyId) : requestedCurrency;
        try {
            return Currency.getInstance(value.trim().toUpperCase(Locale.ROOT)).getCurrencyCode();
        } catch (IllegalArgumentException exception) {
            throw new BusinessException("INVALID_CURRENCY", "Currency code is invalid.", HttpStatus.BAD_REQUEST);
        }
    }
}
