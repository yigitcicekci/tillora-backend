package com.yigitcicekci.tillora.chartofaccount.domain.repository;

import com.yigitcicekci.tillora.chartofaccount.domain.entity.AccountCodeSequence;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AccountCodeSequenceRepository extends JpaRepository<AccountCodeSequence, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AccountCodeSequence> findByCompanyIdAndMainAccountCodeAndGroupCode(UUID companyId, String mainAccountCode, String groupCode);
}
