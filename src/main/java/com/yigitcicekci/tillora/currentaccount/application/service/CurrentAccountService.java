package com.yigitcicekci.tillora.currentaccount.application.service;

import com.yigitcicekci.tillora.chartofaccount.application.service.LedgerAccountProvision;
import com.yigitcicekci.tillora.chartofaccount.application.service.LedgerAccountProvisioningService;
import com.yigitcicekci.tillora.currentaccount.api.request.CreateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.request.UpdateCurrentAccountRequest;
import com.yigitcicekci.tillora.currentaccount.api.response.CurrentAccountResponse;
import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccount;
import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccountLedgerAccount;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.CurrentAccountStatus;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.LedgerRole;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import com.yigitcicekci.tillora.currentaccount.domain.repository.CurrentAccountLedgerAccountRepository;
import com.yigitcicekci.tillora.currentaccount.domain.repository.CurrentAccountRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentAccountService {

    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.0000");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Europe/Istanbul");

    private final CurrentAccountRepository currentAccountRepository;
    private final CurrentAccountLedgerAccountRepository ledgerAccountRepository;
    private final LedgerAccountProvisioningService ledgerAccountProvisioningService;
    private final CurrentAccountMovementService currentAccountMovementService;
    private final AuditLogService auditLogService;

    public CurrentAccountService(
        CurrentAccountRepository currentAccountRepository,
        CurrentAccountLedgerAccountRepository ledgerAccountRepository,
        LedgerAccountProvisioningService ledgerAccountProvisioningService,
        CurrentAccountMovementService currentAccountMovementService,
        AuditLogService auditLogService
    ) {
        this.currentAccountRepository = currentAccountRepository;
        this.ledgerAccountRepository = ledgerAccountRepository;
        this.ledgerAccountProvisioningService = ledgerAccountProvisioningService;
        this.currentAccountMovementService = currentAccountMovementService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public CurrentAccountResponse create(UUID companyId, UUID actorUserId, CreateCurrentAccountRequest request) {
        String name = request.name().trim();
        validateName(companyId, name, request.relationshipType());
        validateIdentifier(companyId, request);
        validateDocumentAddress(request);
        OpeningBalance openingBalance = openingBalance(request);
        CurrentAccount currentAccount = CurrentAccount.create(
            companyId,
            name,
            nullable(request.legalName()),
            nullable(request.taxNumber()),
            nullable(request.taxOffice()),
            nullable(request.identityNumber()),
            nullable(request.phone()),
            nullable(request.email()),
            nullable(request.address()),
            nullable(request.district()),
            nullable(request.city()),
            nullable(request.postalCode()),
            normalizeCountryCode(request.countryCode()),
            nullable(request.countryName()),
            request.tradeType(),
            request.relationshipType()
        );
        CurrentAccount savedCurrentAccount = currentAccountRepository.save(currentAccount);
        List<CurrentAccountLedgerAccount> ledgerAccounts = ledgerAccounts(savedCurrentAccount, request);
        ledgerAccountRepository.saveAll(ledgerAccounts);
        if (openingBalance.hasAmount()) {
            currentAccountMovementService.recordOpeningBalance(
                companyId,
                savedCurrentAccount.id(),
                LocalDate.now(BUSINESS_ZONE),
                openingBalance.debit(),
                openingBalance.credit()
            );
        }
        auditLogService.record(companyId, actorUserId, AuditAction.CURRENT_ACCOUNT_CREATE, "CURRENT_ACCOUNT", savedCurrentAccount.id());
        return CurrentAccountResponse.from(savedCurrentAccount, ledgerAccounts);
    }

    @Transactional(readOnly = true)
    public Page<CurrentAccountResponse> list(UUID companyId, Pageable pageable) {
        Page<CurrentAccount> page = currentAccountRepository.findByCompanyIdAndStatus(companyId, CurrentAccountStatus.ACTIVE, pageable);
        List<UUID> ids = page.getContent().stream().map(CurrentAccount::id).toList();
        if (ids.isEmpty()) {
            return page.map(currentAccount -> CurrentAccountResponse.from(currentAccount, List.of()));
        }
        Map<UUID, List<CurrentAccountLedgerAccount>> ledgerAccounts = ledgerAccountRepository.findByCurrentAccountIdIn(ids).stream()
            .collect(Collectors.groupingBy(CurrentAccountLedgerAccount::currentAccountId));
        List<CurrentAccountResponse> responses = page.getContent().stream()
            .map(currentAccount -> CurrentAccountResponse.from(currentAccount, ledgerAccounts.getOrDefault(currentAccount.id(), List.of())))
            .toList();
        return new PageImpl<>(responses, pageable, page.getTotalElements());
    }

    @Transactional(readOnly = true)
    public CurrentAccountResponse get(UUID companyId, UUID id) {
        CurrentAccount currentAccount = currentAccountRepository.findByIdAndCompanyIdAndStatus(id, companyId, CurrentAccountStatus.ACTIVE)
            .orElseThrow(() -> new BusinessException("CURRENT_ACCOUNT_NOT_FOUND", "Current account not found.", HttpStatus.NOT_FOUND));
        return CurrentAccountResponse.from(currentAccount, ledgerAccountRepository.findByCurrentAccountIdIn(List.of(id)));
    }

    @Transactional(readOnly = true)
    public CurrentAccountEmailSnapshot emailSnapshot(UUID companyId, UUID id) {
        CurrentAccount currentAccount = currentAccountRepository.findByIdAndCompanyIdAndStatus(
            id,
            companyId,
            CurrentAccountStatus.ACTIVE
        ).orElseThrow(() -> new BusinessException(
            "CURRENT_ACCOUNT_NOT_FOUND",
            "Current account not found.",
            HttpStatus.NOT_FOUND
        ));
        return new CurrentAccountEmailSnapshot(currentAccount.id(), currentAccount.name());
    }

    @Transactional
    public CurrentAccountResponse update(UUID companyId, UUID actorUserId, UUID id, UpdateCurrentAccountRequest request) {
        CurrentAccount currentAccount = currentAccountRepository.findForUpdate(id, companyId)
            .orElseThrow(() -> new BusinessException("CURRENT_ACCOUNT_NOT_FOUND", "Current account not found.", HttpStatus.NOT_FOUND));
        if (currentAccount.status() != CurrentAccountStatus.ACTIVE) {
            throw new BusinessException("CURRENT_ACCOUNT_NOT_FOUND", "Current account not found.", HttpStatus.NOT_FOUND);
        }
        String name = request.name().trim();
        String taxNumber = nullable(request.taxNumber());
        String identityNumber = nullable(request.identityNumber());
        validateIdentifier(taxNumber, identityNumber);
        validateDocumentAddress(request.countryCode(), request.countryName());
        boolean nameChanged = !Objects.equals(currentAccount.name(), name);
        boolean changed = currentAccount.update(
            name,
            nullable(request.legalName()),
            taxNumber,
            nullable(request.taxOffice()),
            identityNumber,
            nullable(request.phone()),
            nullable(request.email()),
            nullable(request.address()),
            nullable(request.district()),
            nullable(request.city()),
            nullable(request.postalCode()),
            normalizeCountryCode(request.countryCode()),
            nullable(request.countryName())
        );
        List<CurrentAccountLedgerAccount> ledgerAccounts = ledgerAccountRepository.findByCurrentAccountIdIn(List.of(id));
        if (changed) {
            if (nameChanged) {
                ledgerAccountProvisioningService.renameCurrentAccountPostingAccounts(
                    companyId,
                    ledgerAccounts.stream().map(CurrentAccountLedgerAccount::chartOfAccountId).toList(),
                    name
                );
            }
            currentAccountRepository.flush();
            auditLogService.record(companyId, actorUserId, AuditAction.CURRENT_ACCOUNT_UPDATE, "CURRENT_ACCOUNT", id);
        }
        return CurrentAccountResponse.from(currentAccount, ledgerAccounts);
    }

    @Transactional
    public void delete(UUID companyId, UUID actorUserId, UUID id) {
        CurrentAccount currentAccount = currentAccountRepository.findForUpdate(id, companyId)
            .orElseThrow(() -> new BusinessException("CURRENT_ACCOUNT_NOT_FOUND", "Current account not found.", HttpStatus.NOT_FOUND));
        List<CurrentAccountLedgerAccount> ledgerAccounts = ledgerAccountRepository.findByCurrentAccountIdIn(List.of(id));
        try {
            ledgerAccountRepository.deleteAll(ledgerAccounts);
            ledgerAccountRepository.flush();
            ledgerAccountProvisioningService.deleteUnusedCurrentAccountPostingAccounts(
                companyId,
                ledgerAccounts.stream().map(CurrentAccountLedgerAccount::chartOfAccountId).toList()
            );
            currentAccountRepository.delete(currentAccount);
            currentAccountRepository.flush();
        } catch (DataIntegrityViolationException exception) {
            throw new BusinessException(
                "CURRENT_ACCOUNT_IN_USE",
                "A current account with financial or document records cannot be deleted. Disable it instead.",
                HttpStatus.CONFLICT
            );
        }
        auditLogService.record(companyId, actorUserId, AuditAction.CURRENT_ACCOUNT_DELETE, "CURRENT_ACCOUNT", id);
    }

    @Transactional
    public CurrentAccountPostingReference findActiveReceivablePostingAccount(UUID companyId, UUID currentAccountId) {
        return findActivePostingAccount(
            companyId,
            currentAccountId,
            LedgerRole.RECEIVABLE,
            "COLLECTION_CURRENT_ACCOUNT_INVALID",
            "Collection requires an active customer current account.",
            "Collection requires a receivable posting account."
        );
    }

    @Transactional
    public CurrentAccountPostingReference findActiveSalesInvoiceAccount(
        UUID companyId,
        UUID currentAccountId
    ) {
        return findActivePostingAccount(
            companyId,
            currentAccountId,
            LedgerRole.RECEIVABLE,
            "INVOICE_CURRENT_ACCOUNT_INVALID",
            "Sales invoice requires an active customer current account.",
            "Sales invoice requires a receivable posting account."
        );
    }

    @Transactional
    public CurrentAccountPostingReference findActivePayablePostingAccount(UUID companyId, UUID currentAccountId) {
        return findActivePostingAccount(
            companyId,
            currentAccountId,
            LedgerRole.PAYABLE,
            "PAYMENT_CURRENT_ACCOUNT_INVALID",
            "Payment requires an active supplier current account.",
            "Payment requires a payable posting account."
        );
    }

    @Transactional
    public CurrentAccountPostingReference findActiveTransferReceivablePostingAccount(
        UUID companyId,
        UUID currentAccountId
    ) {
        return findActivePostingAccount(
            companyId,
            currentAccountId,
            LedgerRole.RECEIVABLE,
            "TRANSFER_CURRENT_ACCOUNT_INVALID",
            "Transfer requires an active customer current account.",
            "Transfer requires a receivable posting account."
        );
    }

    @Transactional
    public CurrentAccountPostingReference findActiveTransferPayablePostingAccount(
        UUID companyId,
        UUID currentAccountId
    ) {
        return findActivePostingAccount(
            companyId,
            currentAccountId,
            LedgerRole.PAYABLE,
            "TRANSFER_CURRENT_ACCOUNT_INVALID",
            "Transfer requires an active supplier current account.",
            "Transfer requires a payable posting account."
        );
    }

    @Transactional
    public CurrentAccountPostingReference findActivePurchaseInvoiceAccount(
        UUID companyId,
        UUID currentAccountId
    ) {
        return findActivePostingAccount(
            companyId,
            currentAccountId,
            LedgerRole.PAYABLE,
            "INVOICE_CURRENT_ACCOUNT_INVALID",
            "Purchase invoice requires an active supplier current account.",
            "Purchase invoice requires a payable posting account."
        );
    }

    private CurrentAccountPostingReference findActivePostingAccount(
        UUID companyId,
        UUID currentAccountId,
        LedgerRole role,
        String errorCode,
        String inactiveMessage,
        String missingLedgerMessage
    ) {
        CurrentAccount currentAccount = currentAccountRepository.findActiveForPosting(
            currentAccountId,
            companyId,
            CurrentAccountStatus.ACTIVE
        ).orElseThrow(() -> new BusinessException(
            errorCode,
            inactiveMessage,
            HttpStatus.BAD_REQUEST
        ));
        CurrentAccountLedgerAccount ledgerAccount = ledgerAccountRepository
            .findByCompanyIdAndCurrentAccountIdAndRole(companyId, currentAccountId, role)
            .orElseThrow(() -> new BusinessException(
                errorCode,
                missingLedgerMessage,
                HttpStatus.BAD_REQUEST
            ));
        return new CurrentAccountPostingReference(
            currentAccount.id(),
            currentAccount.name(),
            ledgerAccount.chartOfAccountId(),
            ledgerAccount.fullAccountCode()
        );
    }

    private void validateIdentifier(UUID companyId, CreateCurrentAccountRequest request) {
        validateIdentifier(request.taxNumber(), request.identityNumber());
        if (!isBlank(request.taxNumber()) && currentAccountRepository.existsByCompanyIdAndTaxNumber(companyId, request.taxNumber())) {
            throw new BusinessException("CURRENT_ACCOUNT_TAX_NUMBER_ALREADY_EXISTS", "Current account tax number already exists.", HttpStatus.CONFLICT);
        }
        if (!isBlank(request.identityNumber()) && currentAccountRepository.existsByCompanyIdAndIdentityNumber(companyId, request.identityNumber())) {
            throw new BusinessException("CURRENT_ACCOUNT_IDENTITY_NUMBER_ALREADY_EXISTS", "Current account identity number already exists.", HttpStatus.CONFLICT);
        }
    }

    private OpeningBalance openingBalance(CreateCurrentAccountRequest request) {
        BigDecimal debit = normalizeOpeningAmount(request.openingDebit());
        BigDecimal credit = normalizeOpeningAmount(request.openingCredit());
        if (debit.signum() > 0 && credit.signum() > 0) {
            throw new BusinessException(
                "CURRENT_ACCOUNT_OPENING_BALANCE_INVALID",
                "Current account opening balance must have either a debit or a credit amount.",
                HttpStatus.UNPROCESSABLE_CONTENT
            );
        }
        return new OpeningBalance(debit, credit);
    }

    private BigDecimal normalizeOpeningAmount(BigDecimal value) {
        if (value == null) {
            return ZERO_AMOUNT;
        }
        if (value.signum() < 0) {
            throw new BusinessException(
                "CURRENT_ACCOUNT_OPENING_BALANCE_INVALID",
                "Current account opening balance cannot be negative.",
                HttpStatus.BAD_REQUEST
            );
        }
        try {
            return value.setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException exception) {
            throw new BusinessException(
                "CURRENT_ACCOUNT_OPENING_BALANCE_INVALID",
                "Current account opening balance supports at most 4 decimal places.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private void validateIdentifier(String taxNumber, String identityNumber) {
        if (isBlank(taxNumber) && isBlank(identityNumber)) {
            throw new BusinessException("CURRENT_ACCOUNT_IDENTIFIER_REQUIRED", "Tax number or identity number is required.");
        }
    }

    private void validateName(UUID companyId, String name, RelationshipType relationshipType) {
        List<RelationshipType> conflictingTypes = switch (relationshipType) {
            case CUSTOMER -> List.of(RelationshipType.CUSTOMER, RelationshipType.BOTH);
            case SUPPLIER -> List.of(RelationshipType.SUPPLIER, RelationshipType.BOTH);
            case BOTH -> List.of(RelationshipType.CUSTOMER, RelationshipType.SUPPLIER, RelationshipType.BOTH);
        };
        if (currentAccountRepository.existsByCompanyIdAndNameAndRelationshipTypes(companyId, name, conflictingTypes)) {
            throw new BusinessException(
                "CURRENT_ACCOUNT_NAME_ALREADY_EXISTS",
                "Current account name already exists for this relationship type.",
                HttpStatus.CONFLICT
            );
        }
    }

    private List<CurrentAccountLedgerAccount> ledgerAccounts(CurrentAccount currentAccount, CreateCurrentAccountRequest request) {
        List<CurrentAccountLedgerAccount> ledgerAccounts = new ArrayList<>();
        if (request.relationshipType() == RelationshipType.CUSTOMER || request.relationshipType() == RelationshipType.BOTH) {
            ledgerAccounts.add(createLedgerAccount(currentAccount, LedgerRole.RECEIVABLE, groupCode(request.tradeType())));
        }
        if (request.relationshipType() == RelationshipType.SUPPLIER || request.relationshipType() == RelationshipType.BOTH) {
            ledgerAccounts.add(createLedgerAccount(currentAccount, LedgerRole.PAYABLE, groupCode(request.tradeType())));
        }
        return ledgerAccounts;
    }

    private CurrentAccountLedgerAccount createLedgerAccount(
        CurrentAccount currentAccount,
        LedgerRole role,
        String groupCode
    ) {
        LedgerAccountProvision provision = role == LedgerRole.RECEIVABLE
            ? ledgerAccountProvisioningService.createReceivableLedgerAccount(currentAccount.companyId(), groupCode, currentAccount.name())
            : ledgerAccountProvisioningService.createPayableLedgerAccount(currentAccount.companyId(), groupCode, currentAccount.name());
        return CurrentAccountLedgerAccount.create(
            currentAccount.companyId(),
            currentAccount.id(),
            role,
            provision.chartOfAccountId(),
            provision.mainAccountCode(),
            provision.groupCode(),
            provision.sequenceNumber(),
            provision.fullAccountCode()
        );
    }

    private String groupCode(TradeType tradeType) {
        return tradeType == TradeType.RETAIL ? "01" : "02";
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String normalizeCountryCode(String value) {
        return isBlank(value) ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private void validateDocumentAddress(CreateCurrentAccountRequest request) {
        validateDocumentAddress(request.countryCode(), request.countryName());
    }

    private void validateDocumentAddress(String countryCode, String countryName) {
        if (isBlank(countryCode) != isBlank(countryName)) {
            throw new BusinessException(
                "CURRENT_ACCOUNT_COUNTRY_INVALID",
                "Country code and country name must be provided together.",
                HttpStatus.BAD_REQUEST
            );
        }
    }

    private String nullable(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private record OpeningBalance(BigDecimal debit, BigDecimal credit) {

        private boolean hasAmount() {
            return debit.signum() > 0 || credit.signum() > 0;
        }
    }
}
