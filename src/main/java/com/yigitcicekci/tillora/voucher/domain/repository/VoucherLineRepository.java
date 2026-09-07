package com.yigitcicekci.tillora.voucher.domain.repository;

import com.yigitcicekci.tillora.voucher.domain.entity.VoucherLine;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VoucherLineRepository extends JpaRepository<VoucherLine, UUID> {

    List<VoucherLine> findByVoucherIdAndCompanyIdOrderByLineNumberAsc(UUID voucherId, UUID companyId);

    @Modifying
    @Query("delete from VoucherLine line where line.voucherId = :voucherId and line.companyId = :companyId")
    int deleteAllByVoucherIdAndCompanyId(
        @Param("voucherId") UUID voucherId,
        @Param("companyId") UUID companyId
    );

    @Query("""
        select line.chartOfAccountId as chartOfAccountId,
               coalesce(sum((line.debit - line.credit) * voucher.exchangeRate), 0) as balance
        from VoucherLine line, Voucher voucher
        where line.voucherId = voucher.id
          and line.companyId = voucher.companyId
          and line.companyId = :companyId
          and voucher.status = :status
          and line.chartOfAccountId in :chartOfAccountIds
        group by line.chartOfAccountId
        """)
    List<ApprovedBalance> findBalances(
        @Param("companyId") UUID companyId,
        @Param("chartOfAccountIds") List<UUID> chartOfAccountIds,
        @Param("status") VoucherStatus status
    );

    interface ApprovedBalance {

        UUID getChartOfAccountId();

        BigDecimal getBalance();
    }
}
