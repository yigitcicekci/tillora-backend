package com.yigitcicekci.tillora.objectstorage.application.port;

public class ObjectStorageClientException extends RuntimeException {

    public ObjectStorageClientException(String message) {
        super(message);
    }

    public ObjectStorageClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
