package com.yigitcicekci.tillora.objectstorage.application.service;

import com.yigitcicekci.tillora.objectstorage.domain.entity.StoredObject;
import com.yigitcicekci.tillora.objectstorage.domain.enumeration.StoredObjectStatus;
import com.yigitcicekci.tillora.objectstorage.domain.repository.StoredObjectRepository;
import com.yigitcicekci.tillora.objectstorage.infrastructure.persistence.ObjectStorageLockRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class StoredObjectRegistry {

    private final StoredObjectRepository storedObjectRepository;
    private final ObjectStorageLockRepository lockRepository;

    StoredObjectRegistry(
        StoredObjectRepository storedObjectRepository,
        ObjectStorageLockRepository lockRepository
    ) {
        this.storedObjectRepository = storedObjectRepository;
        this.lockRepository = lockRepository;
    }

    @Transactional
    public StoredObjectReference reserve(
        UUID companyId,
        String bucket,
        String objectKey,
        String contentType,
        String checksum,
        long size
    ) {
        lockRepository.acquire(companyId, objectKey);
        StoredObject storedObject = storedObjectRepository
            .findByCompanyIdAndObjectKey(companyId, objectKey)
            .orElse(null);
        if (storedObject == null) {
            storedObject = storedObjectRepository.saveAndFlush(StoredObject.pending(
                companyId,
                bucket,
                objectKey,
                contentType,
                checksum,
                size
            ));
        } else {
            if (!storedObject.matches(bucket, contentType, checksum, size)) {
                throw new BusinessException(
                    "OBJECT_STORAGE_KEY_CONFLICT",
                    "Object key was already used for different content.",
                    HttpStatus.CONFLICT
                );
            }
            storedObject.prepareRetry();
            storedObjectRepository.saveAndFlush(storedObject);
        }
        return StoredObjectReference.from(storedObject);
    }

    @Transactional
    public StoredObjectReference prepareUpload(UUID companyId, UUID objectId) {
        StoredObject storedObject = findForUpdate(companyId, objectId);
        storedObject.markIntegrityFailed();
        storedObject.prepareRetry();
        return StoredObjectReference.from(storedObjectRepository.saveAndFlush(storedObject));
    }

    @Transactional
    public StoredObjectReference complete(UUID companyId, UUID objectId) {
        StoredObject storedObject = findForUpdate(companyId, objectId);
        storedObject.markAvailable();
        return StoredObjectReference.from(storedObjectRepository.saveAndFlush(storedObject));
    }

    @Transactional
    public void fail(UUID companyId, UUID objectId) {
        StoredObject storedObject = findForUpdate(companyId, objectId);
        storedObject.markFailed();
        storedObjectRepository.saveAndFlush(storedObject);
    }

    @Transactional
    public void integrityFailure(UUID companyId, UUID objectId) {
        StoredObject storedObject = findForUpdate(companyId, objectId);
        storedObject.markIntegrityFailed();
        storedObjectRepository.saveAndFlush(storedObject);
    }

    @Transactional(readOnly = true)
    public StoredObjectReference findAvailable(UUID companyId, UUID objectId) {
        StoredObject storedObject = storedObjectRepository.findByIdAndCompanyId(objectId, companyId)
            .orElseThrow(this::notFound);
        if (storedObject.status() != StoredObjectStatus.AVAILABLE) {
            throw new BusinessException(
                "OBJECT_STORAGE_NOT_AVAILABLE",
                "Stored object is not available.",
                HttpStatus.CONFLICT
            );
        }
        return StoredObjectReference.from(storedObject);
    }

    private StoredObject findForUpdate(UUID companyId, UUID objectId) {
        return storedObjectRepository.findForUpdate(objectId, companyId)
            .orElseThrow(this::notFound);
    }

    private BusinessException notFound() {
        return new BusinessException(
            "STORED_OBJECT_NOT_FOUND",
            "Stored object not found.",
            HttpStatus.NOT_FOUND
        );
    }
}
