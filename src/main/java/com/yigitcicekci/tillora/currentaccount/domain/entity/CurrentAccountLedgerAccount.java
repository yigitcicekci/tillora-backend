package com.yigitcicekci.tillora.currentaccount.domain.entity;

import com.yigitcicekci.tillora.currentaccount.domain.enumeration.LedgerRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "current_account_ledger_accounts")
public class CurrentAccountLedgerAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID currentAccountId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private LedgerRole role;

    @Column(nullable = false)
    private UUID chartOfAccountId;

    @Column(nullable = false, length = 12)
    private String mainAccountCode;

    @Column(nullable = false, length = 12)
    private String groupCode;

    @Column(nullable = false)
    private long sequenceNumber;

    @Column(nullable = false, length = 32)
    private String fullAccountCode;

    @Column(nullable = false)
    private Instant createdAt;

    protected CurrentAccountLedgerAccount() {
    }

    private CurrentAccountLedgerAccount(
        UUID companyId,
        UUID currentAccountId,
        LedgerRole role,
        UUID chartOfAccountId,
        String mainAccountCode,
        String groupCode,
        long sequenceNumber,
        String fullAccountCode
    ) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.currentAccountId = currentAccountId;
        this.role = role;
        this.chartOfAccountId = chartOfAccountId;
        this.mainAccountCode = mainAccountCode;
        this.groupCode = groupCode;
        this.sequenceNumber = sequenceNumber;
        this.fullAccountCode = fullAccountCode;
        this.createdAt = Instant.now();
    }

    public static CurrentAccountLedgerAccount create(
        UUID companyId,
        UUID currentAccountId,
        LedgerRole role,
        UUID chartOfAccountId,
        String mainAccountCode,
        String groupCode,
        long sequenceNumber,
        String fullAccountCode
    ) {
        return new CurrentAccountLedgerAccount(companyId, currentAccountId, role, chartOfAccountId, mainAccountCode, groupCode, sequenceNumber, fullAccountCode);
    }

    public LedgerRole role() {
        return role;
    }

    public UUID currentAccountId() {
        return currentAccountId;
    }

    public UUID chartOfAccountId() {
        return chartOfAccountId;
    }

    public String fullAccountCode() {
        return fullAccountCode;
    }
}
