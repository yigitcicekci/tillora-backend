package com.yigitcicekci.tillora.currentaccount.application.service;

import java.util.UUID;

public record CurrentAccountPostingReference(
    UUID currentAccountId,
    String currentAccountName,
    UUID chartOfAccountId,
    String accountCode
) {
}
