package com.yigitcicekci.tillora.currentaccount.domain.repository;

import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccount;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.CurrentAccountStatus;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CurrentAccountRepository extends JpaRepository<CurrentAccount, UUID> {

    boolean existsByCompanyIdAndTaxNumber(UUID companyId, String taxNumber);

    boolean existsByCompanyIdAndIdentityNumber(UUID companyId, String identityNumber);

    @Query("""
        select count(account) > 0
        from CurrentAccount account
        where account.companyId = :companyId
          and lower(trim(account.name)) = lower(trim(:name))
          and account.relationshipType in :relationshipTypes
        """)
    boolean existsByCompanyIdAndNameAndRelationshipTypes(
        @Param("companyId") UUID companyId,
        @Param("name") String name,
        @Param("relationshipTypes") Collection<RelationshipType> relationshipTypes
    );

    Page<CurrentAccount> findByCompanyIdAndStatus(UUID companyId, CurrentAccountStatus status, Pageable pageable);

    Optional<CurrentAccount> findByIdAndCompanyIdAndStatus(UUID id, UUID companyId, CurrentAccountStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from CurrentAccount account where account.id = :id and account.companyId = :companyId")
    Optional<CurrentAccount> findForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        select account
        from CurrentAccount account
        where account.id = :id
          and account.companyId = :companyId
          and account.status = :status
        """)
    Optional<CurrentAccount> findActiveForPosting(
        @Param("id") UUID id,
        @Param("companyId") UUID companyId,
        @Param("status") CurrentAccountStatus status
    );
}
