package com.yigitcicekci.tillora.finance.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bank_accounts")
public class BankAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(nullable = false, length = 160)
    private String branch;

    @Column(nullable = false, length = 34)
    private String iban;

    @Column(nullable = false, length = 64)
    private String accountNumber;

    @Column(nullable = false)
    private UUID chartOfAccountId;

    @Column(nullable = false, length = 32)
    private String accountCode;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected BankAccount() {
    }

    private BankAccount(
        UUID companyId,
        String name,
        String branch,
        String iban,
        String accountNumber,
        UUID chartOfAccountId,
        String accountCode,
        String currency
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.name = name;
        this.branch = branch;
        this.iban = iban;
        this.accountNumber = accountNumber;
        this.chartOfAccountId = chartOfAccountId;
        this.accountCode = accountCode;
        this.currency = currency;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static BankAccount create(
        UUID companyId,
        String name,
        String branch,
        String iban,
        String accountNumber,
        UUID chartOfAccountId,
        String accountCode,
        String currency
    ) {
        return new BankAccount(
            companyId,
            name,
            branch,
            iban,
            accountNumber,
            chartOfAccountId,
            accountCode,
            currency
        );
    }

    public boolean disable() {
        if (!active) {
            return false;
        }
        active = false;
        updatedAt = Instant.now();
        return true;
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public String name() {
        return name;
    }

    public String branch() {
        return branch;
    }

    public String iban() {
        return iban;
    }

    public String accountNumber() {
        return accountNumber;
    }

    public UUID chartOfAccountId() {
        return chartOfAccountId;
    }

    public String accountCode() {
        return accountCode;
    }

    public String currency() {
        return currency;
    }

    public boolean active() {
        return active;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
