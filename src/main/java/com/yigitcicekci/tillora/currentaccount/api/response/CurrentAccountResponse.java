package com.yigitcicekci.tillora.currentaccount.api.response;

import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccount;
import com.yigitcicekci.tillora.currentaccount.domain.entity.CurrentAccountLedgerAccount;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.CurrentAccountStatus;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record CurrentAccountResponse(
    UUID id,
    UUID companyId,
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
    CurrentAccountStatus status,
    Instant createdAt,
    Instant updatedAt,
    String district,
    String city,
    String postalCode,
    String countryCode,
    String countryName,
    List<CurrentAccountLedgerAccountResponse> ledgerAccounts
) {
    public CurrentAccountResponse(
        UUID id,
        UUID companyId,
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
        CurrentAccountStatus status,
        Instant createdAt,
        Instant updatedAt,
        List<CurrentAccountLedgerAccountResponse> ledgerAccounts
    ) {
        this(
            id,
            companyId,
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
            status,
            createdAt,
            updatedAt,
            null,
            null,
            null,
            null,
            null,
            ledgerAccounts
        );
    }

    public static CurrentAccountResponse from(CurrentAccount currentAccount, List<CurrentAccountLedgerAccount> ledgerAccounts) {
        return new CurrentAccountResponse(
            currentAccount.id(),
            currentAccount.companyId(),
            currentAccount.name(),
            currentAccount.legalName(),
            currentAccount.taxNumber(),
            currentAccount.taxOffice(),
            currentAccount.identityNumber(),
            currentAccount.phone(),
            currentAccount.email(),
            currentAccount.address(),
            currentAccount.tradeType(),
            currentAccount.relationshipType(),
            currentAccount.status(),
            currentAccount.createdAt(),
            currentAccount.updatedAt(),
            currentAccount.district(),
            currentAccount.city(),
            currentAccount.postalCode(),
            currentAccount.countryCode(),
            currentAccount.countryName(),
            ledgerAccounts.stream().map(CurrentAccountLedgerAccountResponse::from).toList()
        );
    }
}
