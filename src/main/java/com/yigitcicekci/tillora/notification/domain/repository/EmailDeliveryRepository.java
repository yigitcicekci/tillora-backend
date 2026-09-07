package com.yigitcicekci.tillora.notification.domain.repository;

import com.yigitcicekci.tillora.notification.domain.entity.EmailDelivery;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryReferenceType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailDeliveryRepository extends JpaRepository<EmailDelivery, UUID> {

    Optional<EmailDelivery> findByCompanyIdAndReferenceTypeAndReferenceIdAndIdempotencyKey(
        UUID companyId,
        EmailDeliveryReferenceType referenceType,
        UUID referenceId,
        String idempotencyKey
    );

    Optional<EmailDelivery> findByIdAndCompanyId(UUID id, UUID companyId);
}
