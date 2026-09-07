package com.yigitcicekci.tillora.objectstorage.application.service;

import com.yigitcicekci.tillora.objectstorage.domain.entity.StoredObject;
import com.yigitcicekci.tillora.objectstorage.domain.enumeration.StoredObjectStatus;
import java.time.Instant;
import java.util.UUID;

public record StoredObjectReference(
    UUID id,
    String objectKey,
    String contentType,
    String checksum,
    long size,
    StoredObjectStatus status,
    Instant createdAt,
    Instant availableAt
) {

    static StoredObjectReference from(StoredObject storedObject) {
        return new StoredObjectReference(
            storedObject.id(),
            storedObject.objectKey(),
            storedObject.contentType(),
            storedObject.checksum(),
            storedObject.size(),
            storedObject.status(),
            storedObject.createdAt(),
            storedObject.availableAt()
        );
    }
}
