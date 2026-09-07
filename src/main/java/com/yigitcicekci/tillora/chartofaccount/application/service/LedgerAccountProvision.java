package com.yigitcicekci.tillora.chartofaccount.application.service;

import java.util.UUID;

public record LedgerAccountProvision(
    UUID chartOfAccountId,
    String mainAccountCode,
    String groupCode,
    long sequenceNumber,
    String fullAccountCode
) {
}
