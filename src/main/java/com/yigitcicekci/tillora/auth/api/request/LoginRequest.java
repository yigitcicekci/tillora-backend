package com.yigitcicekci.tillora.auth.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Email;

public record LoginRequest(
    @NotBlank @Email String email,
    @NotBlank String username,
    @NotBlank String password
) {
}
