package com.yigitcicekci.tillora.company.application.service;

public record BootstrapCompanyCommand(
    String companyName,
    String companyLegalName,
    String companyTaxNumber,
    String currency,
    String timezone,
    String adminUsername,
    String adminEmail,
    String adminPassword,
    String adminFirstName,
    String adminLastName
) {
}
