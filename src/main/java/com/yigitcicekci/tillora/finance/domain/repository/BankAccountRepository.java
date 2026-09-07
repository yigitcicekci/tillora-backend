package com.yigitcicekci.tillora.finance.domain.repository;

import com.yigitcicekci.tillora.finance.domain.entity.BankAccount;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

    boolean existsByCompanyIdAndIban(UUID companyId, String iban);

    boolean existsByCompanyIdAndNameIgnoreCaseAndBranchIgnoreCaseAndAccountNumber(
        UUID companyId,
        String name,
        String branch,
        String accountNumber
    );

    Page<BankAccount> findByCompanyId(UUID companyId, Pageable pageable);

    Page<BankAccount> findByCompanyIdAndActive(UUID companyId, boolean active, Pageable pageable);

    Optional<BankAccount> findByIdAndCompanyId(UUID id, UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select account from BankAccount account where account.id = :id and account.companyId = :companyId")
    Optional<BankAccount> findForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("""
        select account
        from BankAccount account
        where account.id = :id
          and account.companyId = :companyId
          and account.active = true
        """)
    Optional<BankAccount> findActiveForPosting(@Param("id") UUID id, @Param("companyId") UUID companyId);
}
