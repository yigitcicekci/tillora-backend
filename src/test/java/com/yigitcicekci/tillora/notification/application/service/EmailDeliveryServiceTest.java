package com.yigitcicekci.tillora.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountEmailSnapshot;
import com.yigitcicekci.tillora.currentaccount.application.service.CurrentAccountService;
import com.yigitcicekci.tillora.notification.application.port.EmailMessage;
import com.yigitcicekci.tillora.notification.application.port.EmailProvider;
import com.yigitcicekci.tillora.notification.application.port.EmailSendResult;
import com.yigitcicekci.tillora.notification.domain.entity.EmailDelivery;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailAttachmentType;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import com.yigitcicekci.tillora.notification.web.request.SendCurrentAccountStatementEmailRequest;
import com.yigitcicekci.tillora.reporting.application.service.CurrentAccountStatementExportRow;
import com.yigitcicekci.tillora.reporting.application.service.ReportingService;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryServiceTest {

    @Mock private EmailProvider provider;
    @Mock private CurrentAccountService currentAccountService;
    @Mock private ReportingService reportingService;
    @Mock private CompanyService companyService;
    @Mock private EmailDeliveryStateService stateService;

    private EmailDeliveryService service;
    private UUID companyId;
    private UUID actorId;

    @BeforeEach
    void setUp() {
        companyId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        service = new EmailDeliveryService(
            provider,
            new EmailLimits(2, 25 * 1024 * 1024),
            currentAccountService,
            reportingService,
            companyService,
            stateService,
            new EmailDeliveryMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void sendsCurrentAccountStatementAsCsvAndPdfAttachments() {
        UUID currentAccountId = UUID.randomUUID();
        LocalDate dateFrom = LocalDate.of(2026, 7, 1);
        LocalDate dateTo = LocalDate.of(2026, 7, 31);
        CurrentAccountEmailSnapshot currentAccount = new CurrentAccountEmailSnapshot(
            currentAccountId,
            "Customer"
        );
        EmailDelivery pending = EmailDelivery.pendingCurrentAccountStatement(
            companyId,
            currentAccountId,
            EmailProviderType.BREVO,
            "Cari hesap ekstresi",
            List.of("customer@example.com"),
            List.of(),
            List.of(
                EmailAttachmentType.STATEMENT_CSV,
                EmailAttachmentType.STATEMENT_PDF
            ),
            "request-1",
            "a".repeat(64),
            actorId
        );
        CurrentAccountStatementExportRow row = new CurrentAccountStatementExportRow(
            dateFrom,
            "DEVIR-1",
            "OPENING_BALANCE",
            null,
            "Opening balance",
            new BigDecimal("100.0000"),
            new BigDecimal("0.0000"),
            new BigDecimal("100.0000"),
            "TRY",
            new BigDecimal("1.00000000")
        );
        when(provider.enabled()).thenReturn(true);
        when(currentAccountService.emailSnapshot(companyId, currentAccountId)).thenReturn(currentAccount);
        when(reportingService.currentAccountStatementForExport(
            companyId,
            currentAccountId,
            dateFrom,
            dateTo
        )).thenReturn(List.of(row));
        when(companyService.name(companyId)).thenReturn("Example & Co");
        byte[] pdf = "%PDF-1.7\n%%EOF".getBytes(StandardCharsets.US_ASCII);
        when(stateService.prepareCurrentAccountStatement(
            any(), any(), any(), any(), any(), any(), any(), any(), any()
        )).thenReturn(new PreparedEmailDelivery(pending, true));
        when(provider.send(any())).thenReturn(new EmailSendResult("message-1"));
        when(stateService.sent(companyId, pending.id(), "message-1")).thenAnswer(invocation -> {
            pending.markSent("message-1");
            return pending;
        });

        var result = service.sendCurrentAccountStatement(
            companyId,
            actorId,
            currentAccountId,
            dateFrom,
            dateTo,
            "request-1",
            new SendCurrentAccountStatementEmailRequest(
                List.of("customer@example.com"),
                List.of(),
                "Cari hesap ekstresi",
                "Ekstre ekte yer almaktadır.",
                "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdf)
            )
        );

        ArgumentCaptor<EmailMessage> message = ArgumentCaptor.forClass(EmailMessage.class);
        verify(provider).send(message.capture());
        assertThat(message.getValue().htmlContent())
            .contains("Customer", "Example &amp; Co")
            .doesNotContain("Example & Co");
        assertThat(message.getValue().attachments()).hasSize(2);
        assertThat(message.getValue().attachments().get(0).fileName())
            .isEqualTo("current-account-statement.csv");
        assertThat(new String(
            message.getValue().attachments().get(0).content(),
            StandardCharsets.UTF_8
        )).contains("DEVIR-1");
        assertThat(message.getValue().attachments().get(1).fileName())
            .isEqualTo("current-account-statement.pdf");
        assertThat(result.status().name()).isEqualTo("SENT");
    }

    @Test
    void rejectsInvalidFrontendStatementPdf() {
        when(provider.enabled()).thenReturn(true);

        assertThatThrownBy(() -> service.sendCurrentAccountStatement(
            companyId,
            actorId,
            UUID.randomUUID(),
            LocalDate.of(2026, 7, 1),
            LocalDate.of(2026, 7, 31),
            "request-1",
            new SendCurrentAccountStatementEmailRequest(
                List.of("customer@example.com"),
                List.of(),
                "Cari hesap ekstresi",
                "Ekstre ekte yer almaktadır.",
                "not-a-pdf"
            )
        )).isInstanceOfSatisfying(BusinessException.class, exception ->
            assertThat(exception.code()).isEqualTo("EMAIL_ATTACHMENT_INVALID")
        );
        verify(currentAccountService, never()).emailSnapshot(any(), any());
    }
}
