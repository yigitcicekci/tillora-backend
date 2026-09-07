package com.yigitcicekci.tillora.currentaccount.domain.repository;

import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccountMovement;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.CurrentAccountReferenceType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CurrentAccountMovementRepository extends JpaRepository<CurrentAccountMovement, UUID> {

    Optional<CurrentAccountMovement> findByCompanyIdAndCurrentAccountIdAndReferenceTypeAndReferenceId(
        UUID companyId,
        UUID currentAccountId,
        CurrentAccountReferenceType referenceType,
        UUID referenceId
    );
}
