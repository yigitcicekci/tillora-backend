package com.yigitcicekci.tillora.platformadmin.infrastructure.configuration;

import com.yigitcicekci.tillora.platformadmin.application.service.PlatformAdminAuthenticationService;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PlatformAdminBootstrapProperties.class)
public class PlatformAdminBootstrapConfiguration {

    @Bean
    ApplicationRunner platformAdminBootstrapRunner(
        PlatformAdminAuthenticationService authenticationService,
        PlatformAdminBootstrapProperties properties
    ) {
        return arguments -> {
            if (properties.enabled()) {
                authenticationService.bootstrap(properties.email(), properties.password());
            }
        };
    }
}
