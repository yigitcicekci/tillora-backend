package com.yigitcicekci.tillora.company.domain.entity;

import com.yigitcicekci.tillora.company.domain.enumeration.CompanyStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Currency;
import java.util.UUID;

@Entity
@Table(name = "companies")
public class Company {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 240)
    private String legalName;

    @Column(nullable = false, length = 20)
    private String taxNumber;

    @Column(length = 120)
    private String taxOffice;

    @Column(columnDefinition = "text")
    private String address;

    @Column(length = 40)
    private String phone;

    @Column(length = 160)
    private String email;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private CompanyStatus status;

    @Column(nullable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    protected Company() {
    }

    private Company(
        UUID id,
        String name,
        String legalName,
        String taxNumber,
        String taxOffice,
        String address,
        String phone,
        String email,
        String currency,
        String timezone
    ) {
        Instant now = Instant.now();
        this.id = id;
        this.name = name;
        this.legalName = legalName;
        this.taxNumber = taxNumber;
        this.taxOffice = taxOffice;
        this.address = address;
        this.phone = phone;
        this.email = email;
        this.currency = currency;
        this.timezone = timezone;
        this.status = CompanyStatus.ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public static Company create(
        String name,
        String legalName,
        String taxNumber,
        String taxOffice,
        String address,
        String phone,
        String email,
        Currency currency,
        String timezone
    ) {
        return new Company(UUID.randomUUID(), name, legalName, taxNumber, taxOffice, address, phone, email, currency.getCurrencyCode(), timezone);
    }

    public UUID id() {
        return id;
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

    public String address() {
        return address;
    }

    public String phone() {
        return phone;
    }

    public String email() {
        return email;
    }

    public String currency() {
        return currency;
    }

    public String timezone() {
        return timezone;
    }

    public CompanyStatus status() {
        return status;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Instant updatedAt() {
        return updatedAt;
    }
}
