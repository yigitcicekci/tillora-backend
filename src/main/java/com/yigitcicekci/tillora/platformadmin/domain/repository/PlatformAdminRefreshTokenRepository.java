package com.yigitcicekci.tillora.platformadmin.domain.repository;

import com.yigitcicekci.tillora.platformadmin.domain.entity.PlatformAdminRefreshToken;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface PlatformAdminRefreshTokenRepository extends JpaRepository<PlatformAdminRefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PlatformAdminRefreshToken> findByIdAndTokenHash(UUID id, String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
        update PlatformAdminRefreshToken token
        set token.revokedAt = :revokedAt,
            token.version = token.version + 1
        where token.platformAdminId = :platformAdminId
          and token.revokedAt is null
        """)
    int revokeAllByPlatformAdminId(UUID platformAdminId, Instant revokedAt);
}
