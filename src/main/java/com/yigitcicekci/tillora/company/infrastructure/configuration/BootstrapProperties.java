package com.yigitcicekci.tillora.company.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tillora.bootstrap")
public record BootstrapProperties(
    boolean enabled,
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
