package com.yigitcicekci.tillora.finance.domain.repository;

import com.yigitcicekci.tillora.finance.domain.entity.CashAccount;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashAccountRepository extends JpaRepository<CashAccount, UUID> {

    boolean existsByCompanyIdAndNameIgnoreCase(UUID companyId, String name);

    Page<CashAccount> findByCompanyId(UUID companyId, Pageable pageable);

    Page<CashAccount> findByCompanyIdAndActive(UUID companyId, boolean active, Pageable pageable);

    Optional<CashAccount> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from CashAccount account where account.id = :id and account.companyId = :companyId")
    Optional<CashAccount> findForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        select account
        from CashAccount account
        where account.id = :id
          and account.companyId = :companyId
          and account.active = true
        """)
    Optional<CashAccount> findActiveForPosting(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        select account
        from CashAccount account
        where account.companyId = :companyId
          and account.chartOfAccountId in :chartOfAccountIds
        order by account.chartOfAccountId
        """)
    List<CashAccount> findAllForBalanceCheck(
        @Param("companyId") UUID companyId,
        @Param("chartOfAccountIds") List<UUID> chartOfAccountIds
    );
}
