package com.yigitcicekci.tillora.finance.application.service;

import java.util.UUID;

public record FinancialPostingAccountReference(
    UUID financialAccountId,
    String financialAccountName,
    UUID chartOfAccountId,
    String accountCode,
    String currency
) {
}
