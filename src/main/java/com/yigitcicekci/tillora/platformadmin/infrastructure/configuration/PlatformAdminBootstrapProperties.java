package com.yigitcicekci.tillora.platformadmin.infrastructure.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("tillora.platform-admin.bootstrap")
public record PlatformAdminBootstrapProperties(
    boolean enabled,
    String email,
    String password
) {
}
