package com.yigitcicekci.tillora.shared.error;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
    String code,
    String message,
    String path,
    OffsetDateTime timestamp,
    String correlationId
) {
}
