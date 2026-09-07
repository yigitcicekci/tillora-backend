package com.yigitcicekci.tillora.currentaccount.application.service;

import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccountMovement;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.CurrentAccountReferenceType;
import com.yigitcicekci.tillora.currentaccount.domain.repository.CurrentAccountMovementRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CurrentAccountMovementService {

    private final CurrentAccountMovementRepository movementRepository;

    public CurrentAccountMovementService(CurrentAccountMovementRepository movementRepository) {
        this.movementRepository = movementRepository;
    }

    @Transactional
    public CurrentAccountMovementReference recordSalesInvoice(
        UUID companyId,
        UUID currentAccountId,
        UUID invoiceId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal amount
    ) {
        CurrentAccountMovement existing = movementRepository
            .findByCompanyIdAndCurrentAccountIdAndReferenceTypeAndReferenceId(
                companyId,
                currentAccountId,
                CurrentAccountReferenceType.INVOICE,
                invoiceId
            )
            .orElse(null);
        if (existing != null) {
            if (existing.debit().compareTo(amount) != 0) {
                throw new BusinessException(
                    "INVOICE_CURRENT_ACCOUNT_MOVEMENT_CONFLICT",
                    "Invoice current account movement is inconsistent.",
                    HttpStatus.CONFLICT
                );
            }
            return new CurrentAccountMovementReference(existing.id());
        }
        CurrentAccountMovement movement = movementRepository.saveAndFlush(
            CurrentAccountMovement.salesInvoice(
                companyId,
                currentAccountId,
                invoiceId,
                invoiceDate,
                dueDate,
                amount
            )
        );
        return new CurrentAccountMovementReference(movement.id());
    }

    @Transactional
    public CurrentAccountMovementReference recordOpeningBalance(
        UUID companyId,
        UUID currentAccountId,
        LocalDate movementDate,
        BigDecimal debit,
        BigDecimal credit
    ) {
        CurrentAccountMovement existing = movementRepository
            .findByCompanyIdAndCurrentAccountIdAndReferenceTypeAndReferenceId(
                companyId,
                currentAccountId,
                CurrentAccountReferenceType.CURRENT_ACCOUNT,
                currentAccountId
            )
            .orElse(null);
        if (existing != null) {
            if (existing.debit().compareTo(debit) != 0 || existing.credit().compareTo(credit) != 0) {
                throw new BusinessException(
                    "CURRENT_ACCOUNT_OPENING_BALANCE_CONFLICT",
                    "Current account opening balance is inconsistent.",
                    HttpStatus.CONFLICT
                );
            }
            return new CurrentAccountMovementReference(existing.id());
        }
        CurrentAccountMovement movement = movementRepository.saveAndFlush(
            CurrentAccountMovement.openingBalance(
                companyId,
                currentAccountId,
                movementDate,
                debit,
                credit
            )
        );
        return new CurrentAccountMovementReference(movement.id());
    }

    @Transactional
    public CurrentAccountMovementReference recordPurchaseInvoice(
        UUID companyId,
        UUID currentAccountId,
        UUID invoiceId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal amount
    ) {
        CurrentAccountMovement existing = movementRepository
            .findByCompanyIdAndCurrentAccountIdAndReferenceTypeAndReferenceId(
                companyId,
                currentAccountId,
                CurrentAccountReferenceType.INVOICE,
                invoiceId
            )
            .orElse(null);
        if (existing != null) {
            if (existing.credit().compareTo(amount) != 0) {
                throw new BusinessException(
                    "INVOICE_CURRENT_ACCOUNT_MOVEMENT_CONFLICT",
                    "Invoice current account movement is inconsistent.",
                    HttpStatus.CONFLICT
                );
            }
            return new CurrentAccountMovementReference(existing.id());
        }
        CurrentAccountMovement movement = movementRepository.saveAndFlush(
            CurrentAccountMovement.purchaseInvoice(
                companyId,
                currentAccountId,
                invoiceId,
                invoiceDate,
                dueDate,
                amount
            )
        );
        return new CurrentAccountMovementReference(movement.id());
    }
}
