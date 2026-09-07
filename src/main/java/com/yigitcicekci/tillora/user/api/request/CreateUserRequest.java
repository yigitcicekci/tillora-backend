package com.yigitcicekci.tillora.user.api.request;

import com.yigitcicekci.tillora.user.domain.enumeration.RoleName;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.Set;

public record CreateUserRequest(
    @NotBlank @Size(max = 80) String username,
    @NotBlank @Email @Size(max = 160) String email,
    @NotBlank @Size(min = 10, max = 120) String temporaryPassword,
    @NotBlank @Size(max = 100) String firstName,
    @NotBlank @Size(max = 100) String lastName,
    @Size(max = 40) String phone,
    @NotEmpty Set<RoleName> roles
) {
}
