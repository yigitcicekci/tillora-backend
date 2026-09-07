package com.yigitcicekci.tillora.currentaccount.domain.repository;

import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccountLedgerAccount;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.LedgerRole;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrentAccountLedgerAccountRepository extends JpaRepository<CurrentAccountLedgerAccount, UUID> {

    List<CurrentAccountLedgerAccount> findByCurrentAccountIdIn(Collection<UUID> currentAccountIds);

    Optional<CurrentAccountLedgerAccount> findByCompanyIdAndCurrentAccountIdAndRole(
        UUID companyId,
        UUID currentAccountId,
        LedgerRole role
    );
}
