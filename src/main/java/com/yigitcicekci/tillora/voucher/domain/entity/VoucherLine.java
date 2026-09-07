package com.yigitcicekci.tillora.voucher.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "voucher_lines")
public class VoucherLine {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID voucherId;

    @Column(nullable = false)
    private int lineNumber;

    @Column(nullable = false)
    private UUID chartOfAccountId;

    private UUID currentAccountId;

    @Column(name = "chart_account_code", nullable = false, length = 32)
    private String accountCode;

    @Column(name = "chart_account_name", nullable = false, length = 180)
    private String accountName;

    @Column(length = 500)
    private String movementNote;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal debit;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal credit;

    @Column(precision = 19, scale = 6)
    private BigDecimal quantity;

    private LocalDate dueDate;

    @Column(precision = 19, scale = 4)
    private BigDecimal currencyAmount;

    @Column(nullable = false)
    private Instant createdAt;

    protected VoucherLine() {
    }

    private VoucherLine(
        UUID companyId,
        UUID voucherId,
        int lineNumber,
        UUID chartOfAccountId,
        UUID currentAccountId,
        String accountCode,
        String accountName,
        String movementNote,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal quantity,
        LocalDate dueDate
    ) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.voucherId = voucherId;
        this.lineNumber = lineNumber;
        this.chartOfAccountId = chartOfAccountId;
        this.currentAccountId = currentAccountId;
        this.accountCode = accountCode;
        this.accountName = accountName;
        this.movementNote = movementNote;
        this.debit = debit;
        this.credit = credit;
        this.quantity = quantity;
        this.dueDate = dueDate;
        this.createdAt = Instant.now();
    }

    public static VoucherLine create(
        UUID companyId,
        UUID voucherId,
        int lineNumber,
        UUID chartOfAccountId,
        String accountCode,
        String accountName,
        String movementNote,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal quantity,
        LocalDate dueDate
    ) {
        return new VoucherLine(
            companyId,
            voucherId,
            lineNumber,
            chartOfAccountId,
            null,
            accountCode,
            accountName,
            movementNote,
            debit,
            credit,
            quantity,
            dueDate
        );
    }

    public static VoucherLine create(
        UUID companyId,
        UUID voucherId,
        int lineNumber,
        UUID chartOfAccountId,
        UUID currentAccountId,
        String accountCode,
        String accountName,
        String movementNote,
        BigDecimal debit,
        BigDecimal credit,
        BigDecimal quantity,
        LocalDate dueDate
    ) {
        return new VoucherLine(
            companyId,
            voucherId,
            lineNumber,
            chartOfAccountId,
            currentAccountId,
            accountCode,
            accountName,
            movementNote,
            debit,
            credit,
            quantity,
            dueDate
        );
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public UUID voucherId() {
        return voucherId;
    }

    public int lineNumber() {
        return lineNumber;
    }

    public UUID chartOfAccountId() {
        return chartOfAccountId;
    }

    public UUID currentAccountId() {
        return currentAccountId;
    }

    public String accountCode() {
        return accountCode;
    }

    public String accountName() {
        return accountName;
    }

    public String movementNote() {
        return movementNote;
    }

    public BigDecimal debit() {
        return debit;
    }

    public BigDecimal credit() {
        return credit;
    }

    public BigDecimal quantity() {
        return quantity;
    }

    public LocalDate dueDate() {
        return dueDate;
    }

    public BigDecimal currencyAmount() {
        return currencyAmount;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
