package com.yigitcicekci.tillora.invoice.domain.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "invoice_settlement_allocations")
public class InvoiceSettlementAllocation {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false)
    private UUID invoiceId;

    @Column(nullable = false)
    private UUID voucherId;

    @Column(nullable = false)
    private UUID idempotencyKey;

    @Column(nullable = false, length = 64)
    private String requestFingerprint;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant createdAt;

    protected InvoiceSettlementAllocation() {
    }

    private InvoiceSettlementAllocation(
        UUID companyId,
        UUID invoiceId,
        UUID voucherId,
        UUID idempotencyKey,
        String requestFingerprint,
        BigDecimal amount
    ) {
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.invoiceId = invoiceId;
        this.voucherId = voucherId;
        this.idempotencyKey = idempotencyKey;
        this.requestFingerprint = requestFingerprint;
        this.amount = amount;
        this.createdAt = Instant.now();
    }

    public static InvoiceSettlementAllocation create(
        UUID companyId,
        UUID invoiceId,
        UUID voucherId,
        UUID idempotencyKey,
        String requestFingerprint,
        BigDecimal amount
    ) {
        return new InvoiceSettlementAllocation(
            companyId,
            invoiceId,
            voucherId,
            idempotencyKey,
            requestFingerprint,
            amount
        );
    }

    public UUID invoiceId() {
        return invoiceId;
    }

    public String requestFingerprint() {
        return requestFingerprint;
    }
}
