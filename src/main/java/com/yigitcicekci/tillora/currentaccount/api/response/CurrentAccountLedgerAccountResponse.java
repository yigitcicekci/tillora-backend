package com.yigitcicekci.tillora.currentaccount.api.response;

import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccountLedgerAccount;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.LedgerRole;
import java.util.UUID;

public record CurrentAccountLedgerAccountResponse(
    LedgerRole role,
    UUID chartOfAccountId,
    String fullAccountCode
) {
    public static CurrentAccountLedgerAccountResponse from(CurrentAccountLedgerAccount ledgerAccount) {
        return new CurrentAccountLedgerAccountResponse(
            ledgerAccount.role(),
            ledgerAccount.chartOfAccountId(),
            ledgerAccount.fullAccountCode()
        );
    }
}
