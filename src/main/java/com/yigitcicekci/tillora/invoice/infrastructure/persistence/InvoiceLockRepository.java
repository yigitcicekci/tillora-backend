package com.yigitcicekci.tillora.invoice.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceLockRepository {

    private final EntityManager entityManager;

    public InvoiceLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void acquireIdempotency(UUID companyId, UUID idempotencyKey) {
        entityManager.createNativeQuery("""
            SELECT pg_advisory_xact_lock(
                hashtextextended(
                    'invoice:' || CAST(:companyId AS text) || ':' || CAST(:idempotencyKey AS text),
                    0
                )
            )
            """)
            .setParameter("companyId", companyId)
            .setParameter("idempotencyKey", idempotencyKey)
            .getSingleResult();
    }
}
