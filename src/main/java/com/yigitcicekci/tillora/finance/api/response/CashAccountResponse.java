package com.yigitcicekci.tillora.finance.api.response;

import com.yigitcicekci.tillora.finance.domain.entity.CashAccount;
import java.time.Instant;
import java.util.UUID;

public record CashAccountResponse(
    UUID id,
    String name,
    String accountCode,
    String currency,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static CashAccountResponse from(CashAccount cashAccount) {
        return new CashAccountResponse(
            cashAccount.id(),
            cashAccount.name(),
            cashAccount.accountCode(),
            cashAccount.currency(),
            cashAccount.active(),
            cashAccount.createdAt(),
            cashAccount.updatedAt()
        );
    }
}
