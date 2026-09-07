package com.yigitcicekci.tillora.currentaccount.api.request;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record CreateCurrentAccountRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 240) String legalName,
    @Pattern(regexp = "\\d{10}", message = "must be a 10 digit tax number") String taxNumber,
    @Size(max = 120) String taxOffice,
    @Pattern(regexp = "\\d{11}", message = "must be an 11 digit identity number") String identityNumber,
    @Size(max = 40) String phone,
    @Email @Size(max = 160) String email,
    @Size(max = 4000) String address,
    @NotNull TradeType tradeType,
    @NotNull RelationshipType relationshipType,
    @Size(max = 160) String district,
    @Size(max = 160) String city,
    @Size(max = 16) String postalCode,
    @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
    @Size(max = 120) String countryName,
    @JsonAlias({"debit", "openingBalanceDebit"})
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal openingDebit,
    @JsonAlias({"credit", "openingBalanceCredit"})
    @DecimalMin("0.0000") @Digits(integer = 15, fraction = 4) BigDecimal openingCredit
) {
    public CreateCurrentAccountRequest(
        String name,
        String legalName,
        String taxNumber,
        String taxOffice,
        String identityNumber,
        String phone,
        String email,
        String address,
        TradeType tradeType,
        RelationshipType relationshipType,
        String district,
        String city,
        String postalCode,
        String countryCode,
        String countryName
    ) {
        this(
            name,
            legalName,
            taxNumber,
            taxOffice,
            identityNumber,
            phone,
            email,
            address,
            tradeType,
            relationshipType,
            district,
            city,
            postalCode,
            countryCode,
            countryName,
            null,
            null
        );
    }

    public CreateCurrentAccountRequest(
        String name,
        String legalName,
        String taxNumber,
        String taxOffice,
        String identityNumber,
        String phone,
        String email,
        String address,
        TradeType tradeType,
        RelationshipType relationshipType
    ) {
        this(
            name,
            legalName,
            taxNumber,
            taxOffice,
            identityNumber,
            phone,
            email,
            address,
            tradeType,
            relationshipType,
            null,
            null,
            null,
            null,
            null,
            null,
            null
        );
    }
}
