package com.yigitcicekci.tillora.company.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCompanyRequest(
    @Size(max = 512) String provisioningToken,
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 240) String legalName,
    @NotBlank @Pattern(regexp = "\\d{10,11}", message = "must be a 10 or 11 digit tax number") String taxNumber,
    @Size(max = 120) String taxOffice,
    @Size(max = 4000) String address,
    @Size(max = 40) String phone,
    @Email @Size(max = 160) String email,
    @NotBlank @Pattern(regexp = "[A-Za-z]{3}", message = "must be a 3 letter ISO currency code") String currency,
    @NotBlank @Size(max = 64) String timezone,
    @NotBlank @Size(max = 80) String adminUsername,
    @NotBlank @Email @Size(max = 160) String adminEmail,
    @NotBlank @Size(min = 10, max = 120) String adminTemporaryPassword,
    @NotBlank @Size(max = 100) String adminFirstName,
    @NotBlank @Size(max = 100) String adminLastName
) {
}
