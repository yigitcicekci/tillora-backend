package com.yigitcicekci.tillora.objectstorage.infrastructure.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.minio.MinioClient;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Status;
import org.springframework.util.unit.DataSize;

class ObjectStorageHealthIndicatorTest {

    @Test
    void reportsDisabledStorageAsNonBlocking() {
        ObjectStorageHealthIndicator indicator = new ObjectStorageHealthIndicator(
            properties(false),
            provider(null)
        );

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("enabled", false);
    }

    @Test
    void verifiesConfiguredBucketConnection() throws Exception {
        MinioClient client = mock(MinioClient.class);
        when(client.bucketExists(any())).thenReturn(true);
        ObjectStorageHealthIndicator indicator = new ObjectStorageHealthIndicator(
            properties(true),
            provider(client)
        );

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(indicator.health().getDetails()).containsEntry("bucketExists", true);
    }

    @SuppressWarnings("unchecked")
    private ObjectProvider<MinioClient> provider(MinioClient client) {
        ObjectProvider<MinioClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(client);
        return provider;
    }

    private ObjectStorageProperties properties(boolean enabled) {
        return new ObjectStorageProperties(
            enabled,
            "http://localhost:9000",
            "access",
            "secret",
            "bucket",
            DataSize.ofMegabytes(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1),
            Duration.ofSeconds(1)
        );
    }
}
