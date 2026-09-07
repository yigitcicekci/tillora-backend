package com.yigitcicekci.tillora.search.api.response;

import java.util.UUID;

public record SearchResultResponse(
    SearchResultType type,
    UUID id,
    String title,
    String subtitle,
    String reference,
    String status
) {
}
