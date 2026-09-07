package com.yigitcicekci.tillora.voucher.api.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import com.yigitcicekci.tillora.voucher.api.request.CreateCollectionVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.application.service.CollectionVoucherService;
import com.yigitcicekci.tillora.voucher.domain.enumeration.SettlementAccountType;
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

class CollectionVoucherControllerTest {

    private final CollectionVoucherService service = mock(CollectionVoucherService.class);
    private final CollectionVoucherController controller = new CollectionVoucherController(service);

    @Test
    void usesAuthenticatedTenantAndActorForCollectionCreation() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID voucherId = UUID.randomUUID();
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            actorId,
            companyId,
            "accounting",
            Set.of("VOUCHER_CREATE")
        );
        CreateCollectionVoucherRequest request = new CreateCollectionVoucherRequest(
            UUID.randomUUID(),
            UUID.randomUUID(),
            SettlementAccountType.CASH,
            UUID.randomUUID(),
            new BigDecimal("100.0000"),
            null,
            "Collection",
            null
        );
        Instant now = Instant.now();
        VoucherResponse response = new VoucherResponse(
            voucherId,
            "THS-2026-000001",
            VoucherType.COLLECTION,
            LocalDate.of(2026, 7, 16),
            "Collection",
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
