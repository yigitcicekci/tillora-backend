package com.yigitcicekci.tillora.chartofaccount.application.service;

public record PurchaseInvoicePostingAccounts(
    PostingAccountReference tradeGoods,
    PostingAccountReference deductibleVat
) {
}
