package com.yigitcicekci.tillora.voucher.infrastructure.persistence;

import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class VoucherNumberSequenceRepository {

    private final EntityManager entityManager;

    public VoucherNumberSequenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public OptionalLong nextValue(UUID companyId, VoucherType voucherType, int year) {
        List<?> results = entityManager.createNativeQuery("""
            INSERT INTO voucher_number_sequences (
                company_id,
                voucher_type,
                voucher_year,
                current_value
            )
            VALUES (:companyId, :voucherType, :voucherYear, 1)
            ON CONFLICT (company_id, voucher_type, voucher_year)
            DO UPDATE
               SET current_value = voucher_number_sequences.current_value + 1
             WHERE voucher_number_sequences.current_value < 999999
            RETURNING current_value
            """)
            .setParameter("companyId", companyId)
            .setParameter("voucherType", voucherType.name())
            .setParameter("voucherYear", year)
            .getResultList();
        if (results.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(((Number) results.getFirst()).longValue());
    }
}
