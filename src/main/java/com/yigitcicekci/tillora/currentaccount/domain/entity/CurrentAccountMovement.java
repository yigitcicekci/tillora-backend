package com.yigitcicekci.tillora.currentaccount.domain.entity;

import com.yigitcicekci.tillora.currentaccount.domain.enumeration.CurrentAccountMovementType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.CurrentAccountReferenceType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "current_account_movements")
public class CurrentAccountMovement {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID currentAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CurrentAccountMovementType movementType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CurrentAccountReferenceType referenceType;

    @Column(nullable = false)
    private UUID referenceId;

    @Column(nullable = false)
    private LocalDate movementDate;

    private LocalDate dueDate;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal debit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal credit;

    @Column(nullable = false)
    private Instant createdAt;

    protected CurrentAccountMovement() {
    }

    private CurrentAccountMovement(
        UUID companyId,
        UUID currentAccountId,
        CurrentAccountMovementType movementType,
        CurrentAccountReferenceType referenceType,
        UUID referenceId,
        LocalDate movementDate,
        LocalDate dueDate,
        BigDecimal debit,
        BigDecimal credit
    ) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.currentAccountId = currentAccountId;
        this.movementType = movementType;
        this.referenceType = referenceType;
        this.referenceId = referenceId;
        this.movementDate = movementDate;
        this.dueDate = dueDate;
        this.debit = debit;
        this.credit = credit;
        this.createdAt = Instant.now();
    }

    public static CurrentAccountMovement salesInvoice(
        UUID companyId,
        UUID currentAccountId,
        UUID invoiceId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal amount
    ) {
        return new CurrentAccountMovement(
            companyId,
            currentAccountId,
            CurrentAccountMovementType.SALES_INVOICE,
            CurrentAccountReferenceType.INVOICE,
            invoiceId,
            invoiceDate,
            dueDate,
            amount,
            new BigDecimal("0.0000")
        );
    }

    public static CurrentAccountMovement openingBalance(
        UUID companyId,
        UUID currentAccountId,
        LocalDate movementDate,
        BigDecimal debit,
        BigDecimal credit
    ) {
        return new CurrentAccountMovement(
            companyId,
            currentAccountId,
            CurrentAccountMovementType.OPENING_BALANCE,
            CurrentAccountReferenceType.CURRENT_ACCOUNT,
            currentAccountId,
            movementDate,
            null,
            debit,
            credit
        );
    }

    public static CurrentAccountMovement purchaseInvoice(
        UUID companyId,
        UUID currentAccountId,
        UUID invoiceId,
        LocalDate invoiceDate,
        LocalDate dueDate,
        BigDecimal amount
    ) {
        return new CurrentAccountMovement(
            companyId,
            currentAccountId,
            CurrentAccountMovementType.PURCHASE_INVOICE,
            CurrentAccountReferenceType.INVOICE,
            invoiceId,
            invoiceDate,
            dueDate,
            new BigDecimal("0.0000"),
            amount
        );
    }

    public UUID id() {
        return id;
    }

    public UUID referenceId() {
        return referenceId;
    }

    public BigDecimal debit() {
        return debit;
    }

    public BigDecimal credit() {
        return credit;
    }
}
