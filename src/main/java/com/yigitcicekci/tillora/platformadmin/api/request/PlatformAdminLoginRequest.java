package com.yigitcicekci.tillora.platformadmin.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformAdminLoginRequest(
    @NotBlank @Email @Size(max = 160) String email,
    @NotBlank @Size(max = 120) String password
) {
}
