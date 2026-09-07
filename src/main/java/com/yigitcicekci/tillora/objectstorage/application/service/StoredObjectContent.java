package com.yigitcicekci.tillora.objectstorage.application.service;

public record StoredObjectContent(
    StoredObjectReference object,
    byte[] content
) {

    public StoredObjectContent {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
