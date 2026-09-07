package com.yigitcicekci.tillora.notification.web;

import com.yigitcicekci.tillora.notification.application.service.EmailDeliveryService;
import com.yigitcicekci.tillora.notification.web.request.SendCurrentAccountStatementEmailRequest;
import com.yigitcicekci.tillora.notification.web.response.EmailDeliveryResponse;
import com.yigitcicekci.tillora.shared.security.AuthenticatedPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/current-accounts")
public class CurrentAccountStatementEmailController {

    private final EmailDeliveryService service;

    public CurrentAccountStatementEmailController(EmailDeliveryService service) {
        this.service = service;
    }

    @PostMapping("/{currentAccountId}/statement/email")
    @PreAuthorize("hasAuthority('REPORT_VIEW')")
    ResponseEntity<EmailDeliveryResponse> send(
        @AuthenticationPrincipal AuthenticatedPrincipal principal,
        @PathVariable UUID currentAccountId,
        @RequestParam(required = false) LocalDate dateFrom,
        @RequestParam(required = false) LocalDate dateTo,
        @RequestHeader(name = "Idempotency-Key", defaultValue = "") String idempotencyKey,
        @Valid @RequestBody SendCurrentAccountStatementEmailRequest request
    ) {
        EmailDeliveryResponse response = service.sendCurrentAccountStatement(
            principal.companyId(),
            principal.userId(),
            currentAccountId,
            dateFrom,
            dateTo,
            idempotencyKey,
            request
        );
        return ResponseEntity.created(URI.create(
            "/api/v1/current-accounts/" + currentAccountId + "/statement/emails/" + response.id()
        )).body(response);
    }
}
