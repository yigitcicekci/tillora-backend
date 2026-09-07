package com.yigitcicekci.tillora.currentaccount.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateCurrentAccountRequest(
    @NotBlank @Size(max = 180) String name,
    @Size(max = 240) String legalName,
    @Pattern(regexp = "\\d{10}", message = "must be a 10 digit tax number") String taxNumber,
    @Size(max = 120) String taxOffice,
    @Pattern(regexp = "\\d{11}", message = "must be an 11 digit identity number") String identityNumber,
    @Size(max = 40) String phone,
    @Email @Size(max = 160) String email,
    @Size(max = 4000) String address,
    @Size(max = 160) String district,
    @Size(max = 160) String city,
    @Size(max = 16) String postalCode,
    @Pattern(regexp = "[A-Za-z]{2}") String countryCode,
    @Size(max = 120) String countryName
) {
}
