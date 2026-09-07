package com.yigitcicekci.tillora.objectstorage.infrastructure.storage;

import com.yigitcicekci.tillora.objectstorage.application.port.ObjectStorageClient;
import com.yigitcicekci.tillora.objectstorage.application.port.ObjectStorageClientException;

public class DisabledObjectStorageClient implements ObjectStorageClient {

    private final String bucket;
    private final long maxObjectSize;

    public DisabledObjectStorageClient(String bucket, long maxObjectSize) {
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
        throw unavailable();
    }

    @Override
    public void put(String objectKey, String contentType, byte[] content) {
        throw unavailable();
    }

    @Override
    public byte[] get(String objectKey, long expectedSize) {
        throw unavailable();
    }

    private ObjectStorageClientException unavailable() {
        return new ObjectStorageClientException("Object storage is disabled.");
    }
}
