package com.yigitcicekci.tillora.platformadmin.domain.repository;

import com.yigitcicekci.tillora.platformadmin.domain.entity.PlatformAdmin;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdmin, UUID> {

    @Query(value = "select pg_advisory_xact_lock(837451092735)", nativeQuery = true)
    void acquireBootstrapLock();

    Optional<PlatformAdmin> findByEmailIgnoreCase(String email);

    Optional<PlatformAdmin> findByIdAndEnabledTrue(UUID id);

    boolean existsByEmailIgnoreCase(String email);
}
