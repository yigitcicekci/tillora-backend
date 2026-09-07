package com.yigitcicekci.tillora.objectstorage.application.port;

public interface ObjectStorageClient {

    String bucket();

    long maxObjectSize();

    boolean exists(String objectKey);

    void put(String objectKey, String contentType, byte[] content);

    byte[] get(String objectKey, long expectedSize);
}
