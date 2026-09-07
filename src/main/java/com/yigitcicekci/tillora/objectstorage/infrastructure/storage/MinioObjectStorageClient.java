package com.yigitcicekci.tillora.objectstorage.infrastructure.storage;

import com.yigitcicekci.tillora.objectstorage.application.port.ObjectStorageClient;
import com.yigitcicekci.tillora.objectstorage.application.port.ObjectStorageClientException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

public class MinioObjectStorageClient implements ObjectStorageClient {

    private static final Set<String> MISSING_OBJECT_CODES = Set.of(
        "NoSuchKey",
        "NoSuchObject",
        "XMinioInvalidObjectName"
    );
    private static final Set<String> EXISTING_BUCKET_CODES = Set.of(
        "BucketAlreadyExists",
        "BucketAlreadyOwnedByYou"
    );

    private final MinioClient minioClient;
    private final String bucket;
    private final long maxObjectSize;
    private volatile boolean bucketReady;

    public MinioObjectStorageClient(
        MinioClient minioClient,
        String bucket,
        long maxObjectSize
    ) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.maxObjectSize = maxObjectSize;
    }

    @Override
    public String bucket() {
        return bucket;
    }

    @Override
    public long maxObjectSize() {
        return maxObjectSize;
    }

    @Override
    public boolean exists(String objectKey) {
        ensureBucket();
        try {
            minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
            return true;
        } catch (ErrorResponseException exception) {
            if (MISSING_OBJECT_CODES.contains(exception.errorResponse().code())) {
                return false;
            }
            throw failure(exception);
        } catch (Exception exception) {
            throw failure(exception);
        }
    }

    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        ensureBucket();
        try (ByteArrayInputStream input = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .contentType(contentType)
                .stream(input, (long) content.length, -1L)
                .build());
        } catch (Exception exception) {
            throw failure(exception);
        }
    }

    @Override
    public byte[] get(String objectKey, long expectedSize) {
        ensureBucket();
        try {
            StatObjectResponse stat = minioClient.statObject(StatObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build());
            if (stat.size() != expectedSize || stat.size() > maxObjectSize) {
                throw new ObjectStorageClientException("Stored object size is inconsistent.");
            }
            try (GetObjectResponse response = minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectKey)
                .build())) {
                return response.readAllBytes();
            }
        } catch (ObjectStorageClientException exception) {
            throw exception;
        } catch (IOException exception) {
            throw failure(exception);
        } catch (Exception exception) {
            throw failure(exception);
        }
    }

    private synchronized void ensureBucket() {
        if (bucketReady) {
            return;
        }
        try {
            boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucket)
                .build());
            if (!exists) {
                try {
                    minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
                } catch (ErrorResponseException exception) {
                    if (!EXISTING_BUCKET_CODES.contains(exception.errorResponse().code())) {
                        throw exception;
                    }
                }
            }
            bucketReady = true;
        } catch (Exception exception) {
            throw failure(exception);
        }
    }

    private ObjectStorageClientException failure(Exception exception) {
        return new ObjectStorageClientException("Object storage operation failed.", exception);
    }
}
