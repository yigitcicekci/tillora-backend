package com.yigitcicekci.tillora.voucher.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class VoucherIdempotencyRepository {

    private final EntityManager entityManager;

    public VoucherIdempotencyRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void acquire(UUID companyId, UUID idempotencyKey) {
        entityManager.createNativeQuery("""
            SELECT pg_advisory_xact_lock(
                hashtextextended(
                    CAST(:companyId AS text) || ':' || CAST(:idempotencyKey AS text),
                    0
                )
            )
            """)
            .setParameter("companyId", companyId)
            .setParameter("idempotencyKey", idempotencyKey)
            .getSingleResult();
    }
}
