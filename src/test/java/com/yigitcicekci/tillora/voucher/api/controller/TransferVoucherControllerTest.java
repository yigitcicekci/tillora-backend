package com.yigitcicekci.tillora.voucher.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.voucher.api.request.CreateTransferVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.application.service.TransferVoucherService;
import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferSourceAccountType;
import com.yigitcicekci.tillora.voucher.domain.enumeration.TransferTargetAccountType;
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

class TransferVoucherControllerTest {

    private final TransferVoucherService service = mock(TransferVoucherService.class);
    private final TransferVoucherController controller = new TransferVoucherController(service);

    @Test
    void usesAuthenticatedTenantAndActorForTransferCreation() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            actorId, companyId, "accounting", Set.of("VOUCHER_CREATE")
        );
        CreateTransferVoucherRequest request = new CreateTransferVoucherRequest(
            UUID.randomUUID(),
            TransferSourceAccountType.CASH,
            UUID.randomUUID(),
            TransferTargetAccountType.BANK,
            UUID.randomUUID(),
            new BigDecimal("100.0000"),
            null,
            "Transfer",
            null
        );
        Instant now = Instant.now();
        VoucherResponse response = new VoucherResponse(
            voucherId,
            "VRM-2026-000001",
            VoucherType.TRANSFER,
            LocalDate.of(2026, 7, 16),
            "Transfer",
            null,
            "TRY",
            new BigDecimal("1.00000000"),
            new BigDecimal("100.0000"),
            new BigDecimal("100.0000"),
            VoucherStatus.DRAFT,
            actorId,
            request.idempotencyKey(),
            null,
            null,
            null,
            now,
            now,
            null,
            null,
            List.of()
        );
        when(service.create(companyId, actorId, request)).thenReturn(response);

        var created = controller.create(principal, request);

        assertThat(created.getHeaders().getLocation())
            .isEqualTo(URI.create("/api/v1/vouchers/" + voucherId));
        assertThat(created.getBody()).isEqualTo(response);
        verify(service).create(companyId, actorId, request);
    }
}
