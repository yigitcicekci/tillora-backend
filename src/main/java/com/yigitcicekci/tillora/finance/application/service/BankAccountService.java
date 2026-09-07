package com.yigitcicekci.tillora.finance.application.service;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.chartofaccount.application.service.LedgerAccountProvisioningService;
import com.yigitcicekci.tillora.chartofaccount.application.service.PostingAccountProvision;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.finance.api.request.CreateBankAccountRequest;
import com.yigitcicekci.tillora.finance.api.response.BankAccountResponse;
import com.yigitcicekci.tillora.finance.domain.entity.BankAccount;
import com.yigitcicekci.tillora.finance.domain.repository.BankAccountRepository;
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
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final LedgerAccountProvisioningService ledgerAccountProvisioningService;
    private final CompanyService companyService;
    private final AuditLogService auditLogService;

    public BankAccountService(
        BankAccountRepository bankAccountRepository,
        LedgerAccountProvisioningService ledgerAccountProvisioningService,
        CompanyService companyService,
        AuditLogService auditLogService
    ) {
        this.bankAccountRepository = bankAccountRepository;
        this.ledgerAccountProvisioningService = ledgerAccountProvisioningService;
        this.companyService = companyService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public BankAccountResponse create(UUID companyId, UUID actorUserId, CreateBankAccountRequest request) {
        String name = request.name().trim();
        String branch = request.branch().trim();
        String iban = normalizeIban(request.iban());
        String accountNumber = normalizeAccountNumber(request.accountNumber());
        if (bankAccountRepository.existsByCompanyIdAndIban(companyId, iban)) {
            throw new BusinessException(
                "BANK_ACCOUNT_IBAN_ALREADY_EXISTS",
                "Bank account IBAN already exists.",
                HttpStatus.CONFLICT
            );
        }
        if (bankAccountRepository.existsByCompanyIdAndNameIgnoreCaseAndBranchIgnoreCaseAndAccountNumber(
            companyId,
            name,
            branch,
            accountNumber
        )) {
            throw new BusinessException(
                "BANK_ACCOUNT_NUMBER_ALREADY_EXISTS",
                "Bank account number already exists for this bank and branch.",
                HttpStatus.CONFLICT
            );
        }
        String currency = resolveCurrency(companyId, request.currency());
        PostingAccountProvision provision = ledgerAccountProvisioningService.createBankPostingAccount(companyId, name);
        BankAccount bankAccount = bankAccountRepository.saveAndFlush(BankAccount.create(
            companyId,
            name,
            branch,
            iban,
            accountNumber,
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
            AuditAction.BANK_ACCOUNT_CREATE,
            "BANK_ACCOUNT",
            bankAccount.id()
        );
        return BankAccountResponse.from(bankAccount);
    }

    @Transactional(readOnly = true)
    public Page<BankAccountResponse> list(UUID companyId, Boolean active, Pageable pageable) {
        Page<BankAccount> accounts = active == null
            ? bankAccountRepository.findByCompanyId(companyId, pageable)
            : bankAccountRepository.findByCompanyIdAndActive(companyId, active, pageable);
        return accounts.map(BankAccountResponse::from);
    }

    @Transactional(readOnly = true)
    public BankAccountResponse get(UUID companyId, UUID id) {
        return bankAccountRepository.findByIdAndCompanyId(id, companyId)
            .map(BankAccountResponse::from)
            .orElseThrow(() -> new BusinessException("BANK_ACCOUNT_NOT_FOUND", "Bank account not found.", HttpStatus.NOT_FOUND));
    }

    @Transactional
    public BankAccountResponse disable(UUID companyId, UUID actorUserId, UUID id) {
        BankAccount bankAccount = bankAccountRepository.findForUpdate(id, companyId)
            .orElseThrow(() -> new BusinessException("BANK_ACCOUNT_NOT_FOUND", "Bank account not found.", HttpStatus.NOT_FOUND));
        if (bankAccount.disable()) {
            ledgerAccountProvisioningService.disableBankPostingAccount(companyId, bankAccount.chartOfAccountId());
            auditLogService.record(
                companyId,
                actorUserId,
                AuditAction.BANK_ACCOUNT_DISABLE,
                "BANK_ACCOUNT",
                bankAccount.id()
            );
        }
        return BankAccountResponse.from(bankAccount);
    }

    private String normalizeIban(String value) {
        String normalized = value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT);
        boolean validFormat = normalized.matches("[A-Z]{2}[0-9]{2}[A-Z0-9]{11,30}")
            && (!normalized.startsWith("TR") || normalized.matches("TR[0-9]{24}"));
        if (!validFormat || !hasValidIbanChecksum(normalized)) {
            throw new BusinessException("INVALID_IBAN", "IBAN is invalid.", HttpStatus.BAD_REQUEST);
        }
        return normalized;
    }

    private boolean hasValidIbanChecksum(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        int remainder = 0;
        for (int index = 0; index < rearranged.length(); index++) {
            char character = rearranged.charAt(index);
            if (Character.isDigit(character)) {
                remainder = (remainder * 10 + character - '0') % 97;
            } else {
                int value = character - 'A' + 10;
                remainder = (remainder * 10 + value / 10) % 97;
                remainder = (remainder * 10 + value % 10) % 97;
            }
        }
        return remainder == 1;
    }

    private String normalizeAccountNumber(String value) {
        String normalized = value.replaceAll("[\\s./-]+", "").toUpperCase(Locale.ROOT);
        if (!normalized.matches("[A-Z0-9]{1,64}")) {
            throw new BusinessException(
                "INVALID_BANK_ACCOUNT_NUMBER",
                "Bank account number is invalid.",
                HttpStatus.BAD_REQUEST
            );
        }
        return normalized;
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
