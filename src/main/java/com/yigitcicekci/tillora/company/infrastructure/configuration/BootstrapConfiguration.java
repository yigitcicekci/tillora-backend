package com.yigitcicekci.tillora.company.infrastructure.configuration;

import com.yigitcicekci.tillora.company.application.service.BootstrapCompanyCommand;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(BootstrapProperties.class)
public class BootstrapConfiguration {

    @Bean
    ApplicationRunner companyBootstrapRunner(CompanyService companyService, BootstrapProperties properties) {
        return arguments -> {
            if (properties.enabled()) {
                companyService.bootstrap(new BootstrapCompanyCommand(
                    properties.companyName(),
                    properties.companyLegalName(),
                    properties.companyTaxNumber(),
                    properties.currency(),
                    properties.timezone(),
                    properties.adminUsername(),
                    properties.adminEmail(),
                    properties.adminPassword(),
                    properties.adminFirstName(),
                    properties.adminLastName()
                ));
            }
        };
    }
}
