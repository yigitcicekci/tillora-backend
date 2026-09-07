package com.yigitcicekci.tillora.platformadmin.infrastructure.security;

import java.util.UUID;

public record PlatformAdminPrincipal(UUID platformAdminId, String email) {
}
