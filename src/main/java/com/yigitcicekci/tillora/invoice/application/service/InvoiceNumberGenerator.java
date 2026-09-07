package com.yigitcicekci.tillora.invoice.application.service;

import com.yigitcicekci.tillora.invoice.domain.enumeration.InvoiceType;
import com.yigitcicekci.tillora.invoice.infrastructure.persistence.InvoiceNumberSequenceRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class InvoiceNumberGenerator {

    private final InvoiceNumberSequenceRepository sequenceRepository;

    public InvoiceNumberGenerator(InvoiceNumberSequenceRepository sequenceRepository) {
        this.sequenceRepository = sequenceRepository;
    }

    public String next(UUID companyId, InvoiceType invoiceType, int year) {
        OptionalLong value = sequenceRepository.nextValue(companyId, invoiceType, year);
        if (value.isEmpty()) {
            throw new BusinessException(
                "INVOICE_NUMBER_LIMIT_REACHED",
                "Invoice number limit has been reached for the selected year.",
                HttpStatus.CONFLICT
            );
        }
        return String.format(
            Locale.ROOT,
            "%s-%04d-%06d",
            invoiceType == InvoiceType.SALES ? "SF" : "AF",
            year,
            value.getAsLong()
        );
    }
}
