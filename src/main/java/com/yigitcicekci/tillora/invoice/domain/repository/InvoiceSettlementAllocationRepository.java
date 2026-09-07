package com.yigitcicekci.tillora.invoice.domain.repository;

import com.yigitcicekci.tillora.invoice.domain.entity.InvoiceSettlementAllocation;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceSettlementAllocationRepository
    extends JpaRepository<InvoiceSettlementAllocation, UUID> {

    Optional<InvoiceSettlementAllocation> findByCompanyIdAndIdempotencyKey(
        UUID companyId,
        UUID idempotencyKey
    );

    @Query(value = """
        SELECT coalesce(sum(allocation.amount), 0)
        FROM invoice_settlement_allocations allocation
        JOIN vouchers voucher
          ON voucher.id = allocation.voucher_id
         AND voucher.company_id = allocation.company_id
        WHERE allocation.company_id = :companyId
          AND allocation.invoice_id = :invoiceId
          AND voucher.status = 'APPROVED'
        """, nativeQuery = true)
    BigDecimal sumApprovedAmount(
        @Param("companyId") UUID companyId,
        @Param("invoiceId") UUID invoiceId
    );
}
