package com.yigitcicekci.tillora.voucher.application.service;

import com.yigitcicekci.tillora.voucher.api.request.CreateCollectionVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.request.CreatePaymentVoucherRequest;
import com.yigitcicekci.tillora.voucher.api.response.VoucherResponse;
import com.yigitcicekci.tillora.voucher.domain.enumeration.SettlementAccountType;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvoiceSettlementVoucherService {

    private final CollectionVoucherService collectionVoucherService;
    private final PaymentVoucherService paymentVoucherService;
    private final VoucherService voucherService;

    public InvoiceSettlementVoucherService(
        CollectionVoucherService collectionVoucherService,
        PaymentVoucherService paymentVoucherService,
        VoucherService voucherService
    ) {
        this.collectionVoucherService = collectionVoucherService;
        this.paymentVoucherService = paymentVoucherService;
        this.voucherService = voucherService;
    }

    @Transactional
    public InvoiceSettlementVoucherReference createAndApprove(
        UUID companyId,
        UUID actorUserId,
        InvoiceSettlementDirection direction,
        InvoiceSettlementVoucherRequest request
    ) {
        SettlementAccountType settlementAccountType = SettlementAccountType.valueOf(
            request.settlementAccountType().name()
        );
        VoucherResponse draft = direction == InvoiceSettlementDirection.COLLECTION
            ? collectionVoucherService.create(
                companyId,
                actorUserId,
                new CreateCollectionVoucherRequest(
                    request.idempotencyKey(),
                    request.currentAccountId(),
                    settlementAccountType,
                    request.settlementAccountId(),
                    request.amount(),
                    request.voucherDate(),
                    request.movementNote(),
                    request.documentNumber()
                )
            )
            : paymentVoucherService.create(
                companyId,
                actorUserId,
                new CreatePaymentVoucherRequest(
                    request.idempotencyKey(),
                    request.currentAccountId(),
                    settlementAccountType,
                    request.settlementAccountId(),
                    request.amount(),
                    request.voucherDate(),
                    request.movementNote(),
                    request.documentNumber()
                )
            );
        VoucherResponse approved = voucherService.approve(companyId, actorUserId, draft.id());
        return new InvoiceSettlementVoucherReference(approved.id(), approved.voucherNumber());
    }
}
