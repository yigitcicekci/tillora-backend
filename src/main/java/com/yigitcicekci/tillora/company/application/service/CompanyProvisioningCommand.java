package com.yigitcicekci.tillora.company.application.service;

public record CompanyProvisioningCommand(
    String name,
    String legalName,
    String taxNumber,
    String taxOffice,
    String address,
    String phone,
    String email,
    String currency,
    String timezone,
    String adminUsername,
    String adminEmail,
    String adminTemporaryPassword,
    String adminFirstName,
    String adminLastName
) {
}
