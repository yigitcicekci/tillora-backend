package com.yigitcicekci.tillora.user.infrastructure.persistence;

import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class UserAdministrationLockRepository {

    private final EntityManager entityManager;

    public UserAdministrationLockRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public void acquire(UUID companyId) {
        entityManager.createNativeQuery("""
            SELECT pg_advisory_xact_lock(
                hashtextextended('user-administration:' || CAST(:companyId AS text), 0)
            )
            """)
            .setParameter("companyId", companyId)
            .getSingleResult();
    }
}
