package com.yigitcicekci.tillora.finance.api.response;

import com.yigitcicekci.tillora.finance.domain.entity.BankAccount;
import java.time.Instant;
import java.util.UUID;

public record BankAccountResponse(
    UUID id,
    String name,
    String branch,
    String iban,
    String accountNumber,
    String accountCode,
    String currency,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
    public static BankAccountResponse from(BankAccount bankAccount) {
        return new BankAccountResponse(
            bankAccount.id(),
            bankAccount.name(),
            bankAccount.branch(),
            bankAccount.iban(),
            bankAccount.accountNumber(),
            bankAccount.accountCode(),
            bankAccount.currency(),
            bankAccount.active(),
            bankAccount.createdAt(),
            bankAccount.updatedAt()
        );
    }
}
