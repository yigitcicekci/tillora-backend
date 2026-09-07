package com.yigitcicekci.tillora.objectstorage.domain.repository;

import com.yigitcicekci.tillora.objectstorage.domain.entity.StoredObject;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoredObjectRepository extends JpaRepository<StoredObject, UUID> {

    Optional<StoredObject> findByCompanyIdAndObjectKey(UUID companyId, String objectKey);

    Optional<StoredObject> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select storedObject
        from StoredObject storedObject
        where storedObject.id = :id
          and storedObject.companyId = :companyId
        """)
    Optional<StoredObject> findForUpdate(
        @Param("id") UUID id,
        @Param("companyId") UUID companyId
    );
}
