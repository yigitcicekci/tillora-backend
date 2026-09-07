package com.yigitcicekci.tillora.chartofaccount.domain.repository;

import com.yigitcicekci.tillora.chartofaccount.domain.entity.ChartOfAccount;
import com.yigitcicekci.tillora.chartofaccount.domain.enumeration.SystemAccountKey;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChartOfAccountRepository extends JpaRepository<ChartOfAccount, UUID> {

    boolean existsByCompanyId(UUID companyId);

    Page<ChartOfAccount> findByCompanyIdAndActiveTrue(UUID companyId, Pageable pageable);

    Optional<ChartOfAccount> findByIdAndCompanyIdAndActiveTrue(UUID id, UUID companyId);

    Optional<ChartOfAccount> findByCompanyIdAndCodeAndActiveTrue(UUID companyId, String code);

    Optional<ChartOfAccount> findByCompanyIdAndSystemKeyAndActiveTrue(UUID companyId, SystemAccountKey systemKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from ChartOfAccount account where account.companyId = :companyId and account.id in :accountIds")
    List<ChartOfAccount> findAllForUpdate(
        @Param("companyId") UUID companyId,
        @Param("accountIds") Collection<UUID> accountIds
    );

    @Modifying
    @Query("update ChartOfAccount account set account.name = :name, account.updatedAt = CURRENT_TIMESTAMP where account.companyId = :companyId and account.id in :accountIds")
    int renameAll(@Param("companyId") UUID companyId, @Param("accountIds") Collection<UUID> accountIds, @Param("name") String name);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        select account
        from ChartOfAccount account
        where account.companyId = :companyId
          and account.systemKey in :systemKeys
          and account.active = true
        """)
    List<ChartOfAccount> findActiveSystemAccounts(
        @Param("companyId") UUID companyId,
        @Param("systemKeys") Collection<SystemAccountKey> systemKeys
    );

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        select account
        from ChartOfAccount account
        where account.companyId = :companyId
          and account.id in :accountIds
          and account.active = true
          and account.postingAllowed = true
        """)
    List<ChartOfAccount> findActivePostingAccounts(
        @Param("companyId") UUID companyId,
        @Param("accountIds") Collection<UUID> accountIds
    );
}
