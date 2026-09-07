package com.yigitcicekci.tillora.objectstorage.infrastructure.configuration;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("objectStorageHealthIndicator")
public class ObjectStorageHealthIndicator implements HealthIndicator {

    private final ObjectStorageProperties properties;
    private final ObjectProvider<MinioClient> minioClient;

    public ObjectStorageHealthIndicator(
        ObjectStorageProperties properties,
        ObjectProvider<MinioClient> minioClient
    ) {
        this.properties = properties;
        this.minioClient = minioClient;
    }

    @Override
    public Health health() {
        if (!properties.enabled()) {
            return Health.up().withDetail("enabled", false).build();
        }
        MinioClient client = minioClient.getIfAvailable();
        if (client == null) {
            return Health.down().withDetail("reason", "client_unavailable").build();
        }
        try {
            boolean bucketExists = client.bucketExists(BucketExistsArgs.builder()
                .bucket(properties.bucket())
                .build());
            return Health.up()
                .withDetail("enabled", true)
                .withDetail("bucketExists", bucketExists)
                .build();
        } catch (Exception exception) {
            return Health.down()
                .withDetail("error", exception.getClass().getSimpleName())
                .build();
        }
    }
}
