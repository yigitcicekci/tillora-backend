package com.yigitcicekci.tillora.audit.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditDetailsTest {

    @Test
    void acceptsBoundedNonSensitiveState() {
        AuditDetails details = AuditDetails.transition("status", "DRAFT", "APPROVED");

        assertThat(details.before()).containsEntry("status", "DRAFT");
        assertThat(details.after()).containsEntry("status", "APPROVED");
    }

    @Test
    void rejectsSensitiveFields() {
        assertThatThrownBy(() -> new AuditDetails(
            Map.of("passwordHash", "secret"),
            Map.of()
        )).isInstanceOf(IllegalArgumentException.class);
    }
}
