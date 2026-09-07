package com.yigitcicekci.tillora.voucher.application.service;

import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.voucher.domain.enumeration.VoucherType;
import com.yigitcicekci.tillora.voucher.infrastructure.persistence.VoucherNumberSequenceRepository;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class VoucherNumberGenerator {

    private final VoucherNumberSequenceRepository voucherNumberSequenceRepository;

    public VoucherNumberGenerator(VoucherNumberSequenceRepository voucherNumberSequenceRepository) {
        this.voucherNumberSequenceRepository = voucherNumberSequenceRepository;
    }

    public String next(UUID companyId, VoucherType voucherType, int year) {
        OptionalLong nextValue = voucherNumberSequenceRepository.nextValue(companyId, voucherType, year);
        if (nextValue.isEmpty()) {
            throw new BusinessException(
                "VOUCHER_NUMBER_LIMIT_REACHED",
                "Voucher number limit has been reached for the selected year.",
                HttpStatus.CONFLICT
            );
        }
        return String.format(
            Locale.ROOT,
            "%s-%04d-%06d",
            prefix(voucherType),
            year,
            nextValue.getAsLong()
        );
    }

    private String prefix(VoucherType voucherType) {
        return switch (voucherType) {
            case COLLECTION -> "THS";
            case PAYMENT -> "TDI";
            case OFFSET -> "MHS";
            case TRANSFER -> "VRM";
        };
    }
}
