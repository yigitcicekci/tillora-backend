package com.yigitcicekci.tillora.objectstorage.application.service;

import com.yigitcicekci.tillora.objectstorage.application.port.ObjectStorageClient;
import com.yigitcicekci.tillora.objectstorage.application.port.ObjectStorageClientException;
import com.yigitcicekci.tillora.objectstorage.domain.enumeration.StoredObjectStatus;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ObjectStorageService {

    private static final Pattern SEGMENT_PATTERN =
        Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

    private final ObjectStorageClient objectStorageClient;
    private final StoredObjectRegistry storedObjectRegistry;

    public ObjectStorageService(
        ObjectStorageClient objectStorageClient,
        StoredObjectRegistry storedObjectRegistry
    ) {
        this.objectStorageClient = objectStorageClient;
        this.storedObjectRegistry = storedObjectRegistry;
    }

    public StoredObjectReference store(UUID companyId, StoreObjectRequest request) {
        ValidatedObject validated = validate(companyId, request);
        StoredObjectReference reference = storedObjectRegistry.reserve(
            companyId,
            objectStorageClient.bucket(),
            validated.objectKey(),
            validated.type().contentType(),
            validated.checksum(),
            validated.content().length
        );
        if (reference.status() == StoredObjectStatus.AVAILABLE) {
            if (exists(reference.objectKey())) {
                return reference;
            }
            reference = storedObjectRegistry.prepareUpload(companyId, reference.id());
        }
        try {
            objectStorageClient.put(
                reference.objectKey(),
                reference.contentType(),
                validated.content()
            );
            return storedObjectRegistry.complete(companyId, reference.id());
        } catch (ObjectStorageClientException exception) {
            storedObjectRegistry.fail(companyId, reference.id());
            throw unavailable();
        }
    }

    public StoredObjectContent get(UUID companyId, UUID objectId) {
        StoredObjectReference reference = storedObjectRegistry.findAvailable(companyId, objectId);
        byte[] content;
        try {
            content = objectStorageClient.get(reference.objectKey(), reference.size());
        } catch (ObjectStorageClientException exception) {
            throw unavailable();
        }
        if (content.length != reference.size()
            || !checksum(content).equals(reference.checksum())) {
            storedObjectRegistry.integrityFailure(companyId, objectId);
            throw new BusinessException(
                "OBJECT_STORAGE_INTEGRITY_FAILED",
                "Stored object failed its integrity check.",
                HttpStatus.CONFLICT
            );
        }
        return new StoredObjectContent(reference, content);
    }

    public StoredObjectReference reference(UUID companyId, UUID objectId) {
        return storedObjectRegistry.findAvailable(companyId, objectId);
    }

    public long maxObjectSize() {
        return objectStorageClient.maxObjectSize();
    }

    private boolean exists(String objectKey) {
        try {
            return objectStorageClient.exists(objectKey);
        } catch (ObjectStorageClientException exception) {
            throw unavailable();
        }
    }

    private ValidatedObject validate(UUID companyId, StoreObjectRequest request) {
        byte[] content = request == null ? null : request.content();
        if (companyId == null
            || request == null
            || request.type() == null
            || request.pathSegments() == null
            || request.pathSegments().isEmpty()
            || request.pathSegments().size() > 8
            || content == null
            || content.length == 0) {
            throw invalidRequest();
        }
        List<String> segments = request.pathSegments();
        if (segments.stream().anyMatch(segment ->
            segment == null
                || !SEGMENT_PATTERN.matcher(segment).matches()
                || !segment.equals(segment.trim()))) {
            throw invalidRequest();
        }
        if (content.length > objectStorageClient.maxObjectSize()) {
            throw new BusinessException(
                "OBJECT_STORAGE_FILE_TOO_LARGE",
                "Object exceeds the configured size limit.",
                HttpStatus.PAYLOAD_TOO_LARGE
            );
        }
        String objectKey = String.join(
            "/",
            "companies",
            companyId.toString(),
            request.type().directory(),
            String.join("/", segments),
            request.type().fileName()
        );
        if (objectKey.getBytes(StandardCharsets.UTF_8).length > 1024) {
            throw invalidRequest();
        }
        return new ValidatedObject(
            request.type(),
            objectKey,
            checksum(content),
            content
        );
    }

    private String checksum(byte[] content) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content)
            );
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable.", exception);
        }
    }

    private BusinessException invalidRequest() {
        return new BusinessException(
            "OBJECT_STORAGE_REQUEST_INVALID",
            "Object storage request is invalid.",
            HttpStatus.BAD_REQUEST
        );
    }

    private BusinessException unavailable() {
        return new BusinessException(
            "OBJECT_STORAGE_UNAVAILABLE",
            "Object storage is unavailable.",
            HttpStatus.SERVICE_UNAVAILABLE
        );
    }

    private record ValidatedObject(
        StoredObjectType type,
        String objectKey,
        String checksum,
        byte[] content
    ) {
    }
}
