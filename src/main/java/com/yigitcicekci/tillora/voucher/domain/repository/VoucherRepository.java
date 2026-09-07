package com.yigitcicekci.tillora.voucher.domain.repository;

import com.yigitcicekci.tillora.voucher.domain.entity.Voucher;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherSourceType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
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

public interface VoucherRepository extends JpaRepository<Voucher, UUID> {

    Optional<Voucher> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<Voucher> findByCompanyIdAndIdempotencyKey(UUID companyId, UUID idempotencyKey);

    Optional<Voucher> findByCompanyIdAndSourceTypeAndSourceId(
        UUID companyId,
        VoucherSourceType sourceType,
        UUID sourceId
    );

    @Query("""
        select voucher
        from Voucher voucher
        where voucher.companyId = :companyId
          and voucher.voucherType = coalesce(:voucherType, voucher.voucherType)
          and voucher.status = coalesce(:status, voucher.status)
          and voucher.voucherDate >= coalesce(:dateFrom, voucher.voucherDate)
          and voucher.voucherDate <= coalesce(:dateTo, voucher.voucherDate)
        """)
    Page<Voucher> findAllByFilters(
        @Param("companyId") UUID companyId,
        @Param("voucherType") VoucherType voucherType,
        @Param("status") VoucherStatus status,
        @Param("dateFrom") LocalDate dateFrom,
        @Param("dateTo") LocalDate dateTo,
        Pageable pageable
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select voucher from Voucher voucher where voucher.id = :id and voucher.companyId = :companyId")
    Optional<Voucher> findForUpdate(@Param("id") UUID id, @Param("companyId") UUID companyId);
}
