package com.yigitcicekci.tillora.objectstorage.infrastructure.configuration;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "tillora.object-storage")
public record ObjectStorageProperties(
    boolean enabled,
    String endpoint,
    String accessKey,
    String secretKey,
    String bucket,
    DataSize maxObjectSize,
    Duration connectTimeout,
    Duration readTimeout,
    Duration writeTimeout
) {
}
