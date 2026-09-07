package com.yigitcicekci.tillora.objectstorage.domain.entity;

import com.yigitcicekci.tillora.objectstorage.domain.enumeration.StoredObjectStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stored_objects")
public class StoredObject {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 63)
    private String bucket;

    @Column(nullable = false, length = 1024)
    private String objectKey;

    @Column(nullable = false, length = 100)
    private String contentType;

    @Column(nullable = false, length = 64)
    private String checksum;

    @Column(nullable = false)
    private long size;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StoredObjectStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private Instant availableAt;

    @Version
    private long version;

    protected StoredObject() {
    }

    private StoredObject(
        UUID companyId,
        String bucket,
        String objectKey,
        String contentType,
        String checksum,
        long size
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.bucket = bucket;
        this.objectKey = objectKey;
        this.contentType = contentType;
        this.checksum = checksum;
        this.size = size;
        this.status = StoredObjectStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static StoredObject pending(
        UUID companyId,
        String bucket,
        String objectKey,
        String contentType,
        String checksum,
        long size
    ) {
        return new StoredObject(companyId, bucket, objectKey, contentType, checksum, size);
    }

    public boolean matches(String bucket, String contentType, String checksum, long size) {
        return this.bucket.equals(bucket)
            && this.contentType.equals(contentType)
            && this.checksum.equals(checksum)
            && this.size == size;
    }

    public void prepareRetry() {
        if (status == StoredObjectStatus.FAILED) {
            status = StoredObjectStatus.PENDING;
            availableAt = null;
            updatedAt = Instant.now();
        }
    }

    public void markAvailable() {
        Instant now = Instant.now();
        status = StoredObjectStatus.AVAILABLE;
        availableAt = now;
        updatedAt = now;
    }

    public void markFailed() {
        if (status == StoredObjectStatus.PENDING) {
            status = StoredObjectStatus.FAILED;
            availableAt = null;
            updatedAt = Instant.now();
        }
    }

    public void markIntegrityFailed() {
        status = StoredObjectStatus.FAILED;
        availableAt = null;
        updatedAt = Instant.now();
    }

    public UUID id() {
        return id;
    }

    public String bucket() {
        return bucket;
    }

    public String objectKey() {
        return objectKey;
    }

    public String contentType() {
        return contentType;
    }

    public String checksum() {
        return checksum;
    }

    public long size() {
        return size;
    }

    public StoredObjectStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant availableAt() {
        return availableAt;
    }
}
