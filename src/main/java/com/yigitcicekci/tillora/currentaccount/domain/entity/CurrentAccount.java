package com.yigitcicekci.tillora.currentaccount.domain.entity;

import com.yigitcicekci.tillora.currentaccount.domain.enumeration.CurrentAccountStatus;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.RelationshipType;
import com.yigitcicekci.tillora.currentaccount.domain.enumeration.TradeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "current_accounts")
public class CurrentAccount {

    @Id
    private UUID id;

    @Column(nullable = false)
    private UUID companyId;

    @Column(nullable = false, length = 180)
    private String name;

    @Column(length = 240)
    private String legalName;

    @Column(length = 20)
    private String taxNumber;

    @Column(length = 120)
    private String taxOffice;

    @Column(length = 20)
    private String identityNumber;

    @Column(length = 40)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(columnDefinition = "text")
    private String address;

    @Column(length = 160)
    private String district;

    @Column(length = 160)
    private String city;

    @Column(length = 16)
    private String postalCode;

    @Column(length = 2)
    private String countryCode;

    @Column(length = 120)
    private String countryName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TradeType tradeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private RelationshipType relationshipType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CurrentAccountStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected CurrentAccount() {
    }

    private CurrentAccount(
        UUID companyId,
        String name,
        String legalName,
        String taxNumber,
        String taxOffice,
        String identityNumber,
        String phone,
        String email,
        String address,
        String district,
        String city,
        String postalCode,
        String countryCode,
        String countryName,
        TradeType tradeType,
        RelationshipType relationshipType
    ) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID();
        this.companyId = companyId;
        this.name = name;
        this.legalName = legalName;
        this.taxNumber = taxNumber;
        this.taxOffice = taxOffice;
        this.identityNumber = identityNumber;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.district = district;
        this.city = city;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.tradeType = tradeType;
        this.relationshipType = relationshipType;
        this.status = CurrentAccountStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static CurrentAccount create(
        UUID companyId,
        String name,
        String legalName,
        String taxNumber,
        String taxOffice,
        String identityNumber,
        String phone,
        String email,
        String address,
        String district,
        String city,
        String postalCode,
        String countryCode,
        String countryName,
        TradeType tradeType,
        RelationshipType relationshipType
    ) {
        return new CurrentAccount(
            companyId,
            name,
            legalName,
            taxNumber,
            taxOffice,
            identityNumber,
            phone,
            email,
            address,
            district,
            city,
            postalCode,
            countryCode,
            countryName,
            tradeType,
            relationshipType
        );
    }

    public boolean update(
        String name,
        String legalName,
        String taxNumber,
        String taxOffice,
        String identityNumber,
        String phone,
        String email,
        String address,
        String district,
        String city,
        String postalCode,
        String countryCode,
        String countryName
    ) {
        if (Objects.equals(this.name, name)
            && Objects.equals(this.legalName, legalName)
            && Objects.equals(this.taxNumber, taxNumber)
            && Objects.equals(this.taxOffice, taxOffice)
            && Objects.equals(this.identityNumber, identityNumber)
            && Objects.equals(this.phone, phone)
            && Objects.equals(this.email, email)
            && Objects.equals(this.address, address)
            && Objects.equals(this.district, district)
            && Objects.equals(this.city, city)
            && Objects.equals(this.postalCode, postalCode)
            && Objects.equals(this.countryCode, countryCode)
            && Objects.equals(this.countryName, countryName)) {
            return false;
        }
        this.name = name;
        this.legalName = legalName;
        this.taxNumber = taxNumber;
        this.taxOffice = taxOffice;
        this.identityNumber = identityNumber;
        this.phone = phone;
        this.email = email;
        this.address = address;
        this.district = district;
        this.city = city;
        this.postalCode = postalCode;
        this.countryCode = countryCode;
        this.countryName = countryName;
        this.updatedAt = Instant.now();
        return true;
    }

    public static CurrentAccount create(
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
        RelationshipType relationshipType
    ) {
        return create(
            companyId,
            name,
            legalName,
            taxNumber,
            taxOffice,
            identityNumber,
            phone,
            email,
            address,
            null,
            null,
            null,
            null,
            null,
            tradeType,
            relationshipType
        );
    }

    public UUID id() {
        return id;
    }

    public UUID companyId() {
        return companyId;
    }

    public String name() {
        return name;
    }

    public String legalName() {
        return legalName;
    }

    public String taxNumber() {
        return taxNumber;
    }

    public String taxOffice() {
        return taxOffice;
    }

    public String identityNumber() {
        return identityNumber;
    }

    public String phone() {
        return phone;
    }

    public String email() {
        return email;
    }

    public String address() {
        return address;
    }

    public String district() {
        return district;
    }

    public String city() {
        return city;
    }

    public String postalCode() {
        return postalCode;
    }

    public String countryCode() {
        return countryCode;
    }

    public String countryName() {
        return countryName;
    }

    public TradeType tradeType() {
        return tradeType;
    }

    public RelationshipType relationshipType() {
        return relationshipType;
    }

    public CurrentAccountStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
