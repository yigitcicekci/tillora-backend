package com.yigitcicekci.tillora.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.audit.application.service.AuditAction;
import com.yigitcicekci.tillora.audit.application.service.AuditDetails;
import com.yigitcicekci.tillora.audit.application.service.AuditLogService;
import com.yigitcicekci.tillora.notification.domain.entity.EmailDelivery;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailAttachmentType;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailDeliveryReferenceType;
import com.yigitcicekci.tillora.notification.domain.enumeration.EmailProviderType;
import com.yigitcicekci.tillora.notification.domain.repository.EmailDeliveryRepository;
import com.yigitcicekci.tillora.notification.infrastructure.persistence.EmailDeliveryLockRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailDeliveryStateServiceTest {

    @Mock private EmailDeliveryRepository repository;
    @Mock private EmailDeliveryLockRepository lockRepository;
    @Mock private AuditLogService auditLogService;

    private EmailDeliveryStateService service;

    @BeforeEach
    void setUp() {
        service = new EmailDeliveryStateService(repository, lockRepository, auditLogService);
    }

    @Test
    void returnsSameStatementDeliveryForSameFingerprint() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        EmailDelivery existing = EmailDelivery.pendingCurrentAccountStatement(
            companyId,
            currentAccountId,
            EmailProviderType.BREVO,
            "Statement",
            List.of("customer@example.com"),
            List.of(),
            List.of(EmailAttachmentType.STATEMENT_CSV),
            "key-1",
            "a".repeat(64),
            actorId
        );
        when(repository.findByCompanyIdAndReferenceTypeAndReferenceIdAndIdempotencyKey(
            companyId,
            EmailDeliveryReferenceType.CURRENT_ACCOUNT_STATEMENT,
            currentAccountId,
            "key-1"
        )).thenReturn(Optional.of(existing));

        PreparedEmailDelivery repeated = service.prepareCurrentAccountStatement(
            companyId,
            actorId,
            currentAccountId,
            "key-1",
            "a".repeat(64),
            "Statement",
            List.of("customer@example.com"),
            List.of(),
            List.of(EmailAttachmentType.STATEMENT_CSV)
        );

        assertThat(repeated.created()).isFalse();
        assertThat(repeated.delivery().id()).isEqualTo(existing.id());
    }

    @Test
    void createsPendingStatementAndRecordsRequestedAudit() {
        UUID companyId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        when(repository.findByCompanyIdAndReferenceTypeAndReferenceIdAndIdempotencyKey(
            companyId,
            EmailDeliveryReferenceType.CURRENT_ACCOUNT_STATEMENT,
            currentAccountId,
            "key-1"
        )).thenReturn(Optional.empty());
        when(repository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        PreparedEmailDelivery created = service.prepareCurrentAccountStatement(
            companyId,
            actorId,
            currentAccountId,
            "key-1",
            "a".repeat(64),
            "Statement",
            List.of("customer@example.com"),
            List.of(),
            List.of(EmailAttachmentType.STATEMENT_CSV)
        );

        assertThat(created.created()).isTrue();
        assertThat(created.delivery().referenceType())
            .isEqualTo(EmailDeliveryReferenceType.CURRENT_ACCOUNT_STATEMENT);
        assertThat(created.delivery().referenceId()).isEqualTo(currentAccountId);
        verify(auditLogService).record(
            eq(companyId),
            eq(actorId),
            eq(AuditAction.EMAIL_DELIVERY_REQUESTED),
            eq("EMAIL_DELIVERY"),
            eq(created.delivery().id()),
            any(AuditDetails.class)
        );
    }
}
