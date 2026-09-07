package com.yigitcicekci.tillora.invoice.infrastructure.persistence;

import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public class InvoiceNumberSequenceRepository {

    private final EntityManager entityManager;

    public InvoiceNumberSequenceRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    public OptionalLong nextValue(UUID companyId, InvoiceType invoiceType, int year) {
        List<?> results = entityManager.createNativeQuery("""
            INSERT INTO invoice_number_sequences (
                company_id,
                invoice_type,
                invoice_year,
                current_value
            )
            VALUES (:companyId, :invoiceType, :invoiceYear, 1)
            ON CONFLICT (company_id, invoice_type, invoice_year)
            DO UPDATE
               SET current_value = invoice_number_sequences.current_value + 1
             WHERE invoice_number_sequences.current_value < 999999
            RETURNING current_value
            """)
            .setParameter("companyId", companyId)
            .setParameter("invoiceType", invoiceType.name())
            .setParameter("invoiceYear", year)
            .getResultList();
        if (results.isEmpty()) {
            return OptionalLong.empty();
        }
        return OptionalLong.of(((Number) results.getFirst()).longValue());
    }
}
