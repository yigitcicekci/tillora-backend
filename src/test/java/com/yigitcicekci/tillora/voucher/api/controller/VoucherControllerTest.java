package com.yigitcicekci.tillora.voucher.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.voucher.api.request.CancelVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.CreateVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.UpdateVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.VoucherLineRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.application.service.VoucherService;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherStatus;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class VoucherControllerTest {

    private final VoucherService service = mock(VoucherService.class);
    private final VoucherController controller = new VoucherController(service);

    @Test
    void usesAuthenticatedTenantAndActorForVoucherOperations() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();
        UUID debitAccountId = UUID.randomUUID();
        UUID creditAccountId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            actorId,
            companyId,
            "accounting",
            Set.of("VOUCHER_READ", "VOUCHER_CREATE", "VOUCHER_APPROVE", "VOUCHER_CANCEL")
        );
        LocalDate voucherDate = LocalDate.of(2026, 7, 16);
        List<VoucherLineRequest> lines = lines(debitAccountId, creditAccountId);
        CreateVoucherRequest createRequest = new CreateVoucherRequest(
            UUID.randomUUID(),
            VoucherType.OFFSET,
            voucherDate,
            "Opening balance",
            "DOC-1",
            "TRY",
            lines
        );
        UpdateVoucherRequest updateRequest = new UpdateVoucherRequest(
            voucherDate,
            "Updated balance",
            "DOC-2",
            "TRY",
            lines
        );
        CancelVoucherRequest cancelRequest = new CancelVoucherRequest("Incorrect entry");
        VoucherResponse response = response(voucherId, actorId, voucherDate);
        PageRequest pageable = PageRequest.of(0, 20);
        LocalDate dateFrom = voucherDate.minusDays(1);
        LocalDate dateTo = voucherDate.plusDays(1);
        when(service.create(companyId, actorId, createRequest)).thenReturn(response);

        var created = controller.create(principal, createRequest);
        controller.update(principal, voucherId, updateRequest);
        controller.list(
            principal,
            VoucherType.OFFSET,
            VoucherStatus.DRAFT,
            dateFrom,
            dateTo,
            pageable
        );
        controller.get(principal, voucherId);
        controller.approve(principal, voucherId);
        controller.cancel(principal, voucherId, cancelRequest);

        assertThat(created.getHeaders().getLocation())
            .isEqualTo(URI.create("/api/v1/vouchers/" + voucherId));
        assertThat(created.getBody()).isEqualTo(response);
        verify(service).create(companyId, actorId, createRequest);
        verify(service).update(companyId, actorId, voucherId, updateRequest);
        verify(service).list(
            companyId,
            VoucherType.OFFSET,
            VoucherStatus.DRAFT,
            dateFrom,
            dateTo,
            pageable
        );
        verify(service).get(companyId, voucherId);
        verify(service).approve(companyId, actorId, voucherId);
        verify(service).cancel(companyId, actorId, voucherId, "Incorrect entry");
    }

    private List<VoucherLineRequest> lines(UUID debitAccountId, UUID creditAccountId) {
        return List.of(
            new VoucherLineRequest(
                debitAccountId,
                "Debit",
                new BigDecimal("100.0000"),
                BigDecimal.ZERO,
                null,
                null
            ),
            new VoucherLineRequest(
                creditAccountId,
                "Credit",
                BigDecimal.ZERO,
                new BigDecimal("100.0000"),
                null,
                null
            )
        );
    }

    private VoucherResponse response(UUID voucherId, UUID actorId, LocalDate voucherDate) {
        Instant now = Instant.now();
        return new VoucherResponse(
            voucherId,
            "MHS-2026-000001",
            VoucherType.OFFSET,
            voucherDate,
            "Opening balance",
            "DOC-1",
            "TRY",
            new BigDecimal("1.00000000"),
            new BigDecimal("100.0000"),
            new BigDecimal("100.0000"),
            VoucherStatus.DRAFT,
            actorId,
            null,
            null,
            null,
            null,
            now,
            now,
            null,
            null,
            List.of()
        );
    }
}
