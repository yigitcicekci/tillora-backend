package com.yigitcicekci.tillora.auth.domain.repository;

import com.yigitcicekci.tillora.auth.domain.entity.AuthRefreshToken;
import java.util.Optional;
import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthRefreshToken> findByIdAndTokenHash(UUID id, String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
        update AuthRefreshToken token
        set token.revokedAt = :revokedAt,
            token.version = token.version + 1
        where token.userId = :userId
          and token.companyId = :companyId
          and token.revokedAt is null
        """)
    int revokeAllByUserIdAndCompanyId(UUID userId, UUID companyId, Instant revokedAt);
}
