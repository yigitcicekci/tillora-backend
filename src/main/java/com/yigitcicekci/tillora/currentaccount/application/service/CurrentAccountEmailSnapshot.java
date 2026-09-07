package com.yigitcicekci.tillora.currentaccount.application.service;

import java.util.UUID;

public record CurrentAccountEmailSnapshot(
    UUID id,
    String name
) {
}
