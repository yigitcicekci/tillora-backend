package com.yigitcicekci.tillora.chartofaccount.application.service;

public record SalesInvoicePostingAccounts(
    PostingAccountReference domesticSales,
    PostingAccountReference calculatedVat,
    PostingAccountReference tradeGoods,
    PostingAccountReference costOfGoodsSold
) {
}
