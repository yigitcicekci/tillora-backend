package com.yigitcicekci.tillora.objectstorage.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class ObjectStorageLockRepository {

    private final EntityManager entityManager;

    public ObjectStorageLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void acquire(UUID companyId, String objectKey) {
        entityManager.createNativeQuery(
            "SELECT pg_advisory_xact_lock(hashtextextended(CAST(:lockKey AS text), 0))"
        )
            .setParameter("lockKey", companyId + ":" + objectKey)
            .getSingleResult();
    }
}
