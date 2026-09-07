package com.yigitcicekci.tillora.voucher.domain.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class VoucherTest {

    @Test
    void transitionsFromDraftToApprovedAndCancelledWithoutChangingFinancialIdentity() {
        UUID companyId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID approverId = UUID.randomUUID();
        UUID cancellingActorId = UUID.randomUUID();
        Voucher voucher = Voucher.create(
            companyId,
            "MHS-2026-000001",
            VoucherType.OFFSET,
            LocalDate.of(2026, 7, 16),
            "Opening balance",
            "DOC-1",
            "TRY",
            new BigDecimal("1.00000000"),
            creatorId,
            new BigDecimal("100.0000"),
            new BigDecimal("100.0000")
        );

        assertThat(voucher.status()).isEqualTo(VoucherStatus.DRAFT);
        assertThat(voucher.approvedAt()).isNull();
        assertThat(voucher.cancelledAt()).isNull();

        voucher.approve(approverId);
        var approvedAt = voucher.approvedAt();
        voucher.cancel(cancellingActorId, "Incorrect entry");

        assertThat(voucher.status()).isEqualTo(VoucherStatus.CANCELLED);
        assertThat(voucher.companyId()).isEqualTo(companyId);
        assertThat(voucher.voucherNumber()).isEqualTo("MHS-2026-000001");
        assertThat(voucher.voucherType()).isEqualTo(VoucherType.OFFSET);
        assertThat(voucher.createdBy()).isEqualTo(creatorId);
        assertThat(voucher.approvedBy()).isEqualTo(approverId);
        assertThat(voucher.approvedAt()).isEqualTo(approvedAt);
        assertThat(voucher.cancelledBy()).isEqualTo(cancellingActorId);
        assertThat(voucher.cancellationReason()).isEqualTo("Incorrect entry");
        assertThat(voucher.cancelledAt()).isNotNull();
        assertThat(voucher.totalDebit()).isEqualByComparingTo("100.0000");
        assertThat(voucher.totalCredit()).isEqualByComparingTo("100.0000");
    }
}
