package com.yigitcicekci.tillora.company.api.response;

import com.yigitcicekci.tillora.company.domain.entity.Company;
import com.yigitcicekci.tillora.company.domain.enumeration.CompanyStatus;
import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
    UUID id,
    String name,
    String legalName,
    String taxNumber,
    String taxOffice,
    String address,
    String phone,
    String email,
    String currency,
    String timezone,
    CompanyStatus status,
    Instant createdAt,
    Instant updatedAt
) {
    public static CompanyResponse from(Company company) {
        return new CompanyResponse(
            company.id(),
            company.name(),
            company.legalName(),
            company.taxNumber(),
            company.taxOffice(),
            company.address(),
            company.phone(),
            company.email(),
            company.currency(),
            company.timezone(),
            company.status(),
            company.createdAt(),
            company.updatedAt()
        );
    }
}
