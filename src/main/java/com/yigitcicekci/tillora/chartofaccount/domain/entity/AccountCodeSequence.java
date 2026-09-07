package com.yigitcicekci.tillora.chartofaccount.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "account_code_sequences")
public class AccountCodeSequence {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 12)
    private String mainAccountCode;

    @Column(nullable = false, length = 12)
    private String groupCode;

    @Column(nullable = false)
    private long currentValue;

    @Version
    private long version;

    protected AccountCodeSequence() {
    }

    private AccountCodeSequence(UUID companyId, String mainAccountCode, String groupCode) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.mainAccountCode = mainAccountCode;
        this.groupCode = groupCode;
        this.currentValue = 0;
    }

    public static AccountCodeSequence create(UUID companyId, String mainAccountCode, String groupCode) {
        return new AccountCodeSequence(companyId, mainAccountCode, groupCode);
    }

    public long nextValue() {
        currentValue = currentValue + 1;
        return currentValue;
    }
}
