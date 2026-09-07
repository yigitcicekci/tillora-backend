package com.yigitcicekci.tillora.notification.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryReferenceType;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class EmailDeliveryLockRepository {

    private final EntityManager entityManager;

    public EmailDeliveryLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void acquire(
        UUID companyId,
        EmailDeliveryReferenceType referenceType,
        UUID referenceId,
        String idempotencyKey
    ) {
        entityManager.createNativeQuery(
            "select pg_advisory_xact_lock(hashtextextended(:lockKey, 0))"
        )
            .setParameter("lockKey", companyId + ":" + referenceType + ":" + referenceId + ":" + idempotencyKey)
            .getSingleResult();
    }
}
