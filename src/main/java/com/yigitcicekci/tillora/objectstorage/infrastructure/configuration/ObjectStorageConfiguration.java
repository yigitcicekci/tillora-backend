package com.yigitcicekci.tillora.objectstorage.infrastructure.configuration;

import com.yigitcicekci.tillora.objectstorage.application.port.ObjectStorageClient;
import com.yigitcicekci.tillora.objectstorage.infrastructure.storage.DisabledObjectStorageClient;
import com.yigitcicekci.tillora.objectstorage.infrastructure.storage.MinioObjectStorageClient;
import io.minio.MinioClient;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(ObjectStorageProperties.class)
public class ObjectStorageConfiguration {

    @Bean
    @ConditionalOnProperty(
        prefix = "tillora.object-storage",
        name = "enabled",
        havingValue = "true"
    )
    MinioClient minioClient(ObjectStorageProperties properties) {
        validate(properties);
        OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(properties.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .readTimeout(properties.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .writeTimeout(properties.writeTimeout().toMillis(), TimeUnit.MILLISECONDS)
            .build();
        return MinioClient.builder()
            .endpoint(properties.endpoint())
            .credentials(properties.accessKey(), properties.secretKey())
            .httpClient(httpClient, true)
            .build();
    }

    @Bean
    @ConditionalOnProperty(
        prefix = "tillora.object-storage",
        name = "enabled",
        havingValue = "true"
    )
    ObjectStorageClient minioObjectStorageClient(
        MinioClient minioClient,
        ObjectStorageProperties properties
    ) {
        return new MinioObjectStorageClient(
            minioClient,
            properties.bucket(),
            properties.maxObjectSize().toBytes()
        );
    }

    @Bean
    @ConditionalOnMissingBean(ObjectStorageClient.class)
    ObjectStorageClient disabledObjectStorageClient(ObjectStorageProperties properties) {
        return new DisabledObjectStorageClient(
            properties.bucket(),
            properties.maxObjectSize().toBytes()
        );
    }

    private void validate(ObjectStorageProperties properties) {
        if (isBlank(properties.endpoint())
            || isBlank(properties.accessKey())
            || isBlank(properties.secretKey())
            || isBlank(properties.bucket())
            || properties.maxObjectSize() == null
            || properties.maxObjectSize().toBytes() <= 0
            || properties.connectTimeout() == null
            || properties.connectTimeout().isNegative()
            || properties.connectTimeout().isZero()
            || properties.readTimeout() == null
            || properties.readTimeout().isNegative()
            || properties.readTimeout().isZero()
            || properties.writeTimeout() == null
            || properties.writeTimeout().isNegative()
            || properties.writeTimeout().isZero()) {
            throw new IllegalStateException("Object storage configuration is invalid.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
