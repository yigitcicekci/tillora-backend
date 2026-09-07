package com.yigitcicekci.tillora.invoice.domain.repository;

import com.yigitcicekci.tillora.invoice.domain.entity.Invoice;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceStatus;
import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    Optional<Invoice> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Invoice> findByCompanyIdAndIdempotencyKey(UUID companyId, UUID idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invoice from Invoice invoice where invoice.id = :id and invoice.companyId = :companyId")
    Optional<Invoice> findForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);

    @Query("""
        select invoice
        from Invoice invoice
        where invoice.companyId = :companyId
          and invoice.invoiceType = coalesce(:invoiceType, invoice.invoiceType)
          and invoice.status = coalesce(:status, invoice.status)
          and invoice.invoiceDate >= :dateFrom
          and invoice.invoiceDate <= :dateTo
        """)
    Page<Invoice> findAllByFilters(
        @Param("companyId") UUID companyId,
        @Param("invoiceType") InvoiceType invoiceType,
        @Param("status") InvoiceStatus status,
        @Param("dateFrom") LocalDate dateFrom,
        @Param("dateTo") LocalDate dateTo,
        Pageable pageable
    );
}
