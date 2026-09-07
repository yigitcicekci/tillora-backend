package com.yigitcicekci.tillora.platformadmin.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PlatformAdminChangePasswordRequest(
    @NotBlank String currentPassword,
    @NotBlank @Size(min = 10, max = 120) String newPassword
) {
}
