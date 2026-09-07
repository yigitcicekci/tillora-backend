package com.yigitcicekci.tillora.invoice.domain.repository;

import com.yigitcicekci.tillora.invoice.domain.entity.InvoiceLine;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, UUID> {

    List<InvoiceLine> findByInvoiceIdAndCompanyIdOrderByLineNumberAsc(UUID invoiceId, UUID companyId);

    @Modifying
    @Query("delete from InvoiceLine line where line.invoiceId = :invoiceId and line.companyId = :companyId")
    int deleteAllByInvoiceIdAndCompanyId(
        @Param("invoiceId") UUID invoiceId,
        @Param("companyId") UUID companyId
    );
}
