package com.yigitcicekci.tillora.chartofaccount.domain.entity;

import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.AccountNature;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.ChartAccountCategory;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.SystemAccountKey;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "chart_of_accounts")
public class ChartOfAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 32)
    private String code;

    @Column(nullable = false, length = 180)
    private String name;

    private UUID parentId;

    @Column(nullable = false)
    private int level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ChartAccountCategory category;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountNature nature;

    @Column(nullable = false)
    private boolean postingAllowed;

    @Enumerated(EnumType.STRING)
    @Column(length = 60)
    private SystemAccountKey systemKey;

    @Column(nullable = false)
    private boolean active;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected ChartOfAccount() {
    }

    private ChartOfAccount(
        UUID companyId,
        String code,
        String name,
        UUID parentId,
        int level,
        ChartAccountCategory category,
        AccountNature nature,
        boolean postingAllowed,
        SystemAccountKey systemKey
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.code = code;
        this.name = name;
        this.parentId = parentId;
        this.level = level;
        this.category = category;
        this.nature = nature;
        this.postingAllowed = postingAllowed;
        this.systemKey = systemKey;
        this.active = true;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static ChartOfAccount systemAccount(
        UUID companyId,
        String code,
        String name,
        ChartAccountCategory category,
        AccountNature nature,
        SystemAccountKey systemKey
    ) {
        return new ChartOfAccount(companyId, code, name, null, 1, category, nature, false, systemKey);
    }

    public static ChartOfAccount ledgerAccount(
        UUID companyId,
        String code,
        String name,
        UUID parentId,
        ChartAccountCategory category,
        AccountNature nature
    ) {
        return postingAccount(companyId, code, name, parentId, 3, category, nature);
    }

    public static ChartOfAccount postingAccount(
        UUID companyId,
        String code,
        String name,
        UUID parentId,
        int level,
        ChartAccountCategory category,
        AccountNature nature
    ) {
        return new ChartOfAccount(companyId, code, name, parentId, level, category, nature, true, null);
    }

    public void deactivate() {
        if (active) {
            active = false;
            updatedAt = Instant.now();
        }
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public String code() {
        return code;
    }

    public String name() {
        return name;
    }

    public UUID parentId() {
        return parentId;
    }

    public int level() {
        return level;
    }

    public ChartAccountCategory category() {
        return category;
    }

    public AccountNature nature() {
        return nature;
    }

    public boolean postingAllowed() {
        return postingAllowed;
    }

    public SystemAccountKey systemKey() {
        return systemKey;
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
