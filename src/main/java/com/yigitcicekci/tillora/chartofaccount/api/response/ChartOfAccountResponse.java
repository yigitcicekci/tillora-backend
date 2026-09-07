package com.yigitcicekci.tillora.chartofaccount.api.response;

import com.yigitcicekci.tillora.chartofaccount.domain.entity.ChartOfAccount;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.AccountNature;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.ChartAccountCategory;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.SystemAccountKey;
import java.time.Instant;
import java.util.UUID;

public record ChartOfAccountResponse(
    UUID id,
    UUID companyId,
    String code,
    String name,
    UUID parentId,
    int level,
    ChartAccountCategory category,
    AccountNature nature,
    boolean postingAllowed,
    SystemAccountKey systemKey,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static ChartOfAccountResponse from(ChartOfAccount account) {
        return new ChartOfAccountResponse(
            account.id(),
            account.companyId(),
            account.code(),
            account.name(),
            account.parentId(),
            account.level(),
            account.category(),
            account.nature(),
            account.postingAllowed(),
            account.systemKey(),
            account.active(),
            account.createdAt(),
            account.updatedAt()
        );
    }
}
