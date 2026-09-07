package com.yigitcicekci.tillora.notification.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.notification.application.service.EmailDeliveryService;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryStatus;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import com.yigitcicekci.tillora.notification.web.request.SendCurrentAccountStatementEmailRequest;
import com.yigitcicekci.tillora.notification.web.response.EmailDeliveryResponse;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class CurrentAccountStatementEmailControllerTest {

    private final EmailDeliveryService service = mock(EmailDeliveryService.class);
    private final CurrentAccountStatementEmailController controller =
        new CurrentAccountStatementEmailController(service);

    @Test
    void usesAuthenticatedCompanyAndActorAndRequiresReportPermission() throws Exception {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        LocalDate dateFrom = LocalDate.of(2026, 7, 1);
        LocalDate dateTo = LocalDate.of(2026, 7, 31);
        AuthenticatedPrincipal principal = new AuthenticatedPrincipal(
            actorId,
            companyId,
            "accounting",
            Set.of("REPORT_VIEW")
        );
        SendCurrentAccountStatementEmailRequest request = new SendCurrentAccountStatementEmailRequest(
            List.of("customer@example.com"),
            List.of(),
            "Cari hesap ekstresi",
            "Ekstre ekte yer almaktadır.",
            Base64.getEncoder().encodeToString(
                "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII)
            )
        );
        EmailDeliveryResponse response = new EmailDeliveryResponse(
            UUID.randomUUID(),
            EmailDeliveryStatus.SENT,
            EmailProviderType.BREVO,
            "message-1",
            Instant.now(),
            Instant.now(),
            null
        );
        when(service.sendCurrentAccountStatement(
            companyId,
            actorId,
            currentAccountId,
            dateFrom,
            dateTo,
            "key-1",
            request
        )).thenReturn(response);

        assertThat(controller.send(
            principal,
            currentAccountId,
            dateFrom,
            dateTo,
            "key-1",
            request
        ).getBody()).isEqualTo(response);
        verify(service).sendCurrentAccountStatement(
            companyId,
            actorId,
            currentAccountId,
            dateFrom,
            dateTo,
            "key-1",
            request
        );

        Method method = CurrentAccountStatementEmailController.class.getDeclaredMethod(
            "send",
            AuthenticatedPrincipal.class,
            UUID.class,
            LocalDate.class,
            LocalDate.class,
            String.class,
            SendCurrentAccountStatementEmailRequest.class
        );
        assertThat(method.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('REPORT_VIEW')");
    }
}
