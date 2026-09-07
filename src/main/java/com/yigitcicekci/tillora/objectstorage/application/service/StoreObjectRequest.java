package com.yigitcicekci.tillora.objectstorage.application.service;

import java.util.List;

public record StoreObjectRequest(
    StoredObjectType type,
    List<String> pathSegments,
    byte[] content
) {

    public StoreObjectRequest {
        pathSegments = pathSegments == null ? null : List.copyOf(pathSegments);
        content = content == null ? null : content.clone();
    }

    @Override
    public byte[] content() {
        return content == null ? null : content.clone();
    }
}
