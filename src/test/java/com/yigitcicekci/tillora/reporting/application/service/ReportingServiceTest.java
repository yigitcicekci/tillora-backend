package com.yigitcicekci.tillora.reporting.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.reporting.api.response.CurrentAccountStatementRow;
import com.yigitcicekci.tillora.reporting.infrastructure.persistence.ReportingQueryRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class ReportingServiceTest {

    @Mock private ReportingQueryRepository queryRepository;
    @Mock private CompanyService companyService;

    private ReportingService service;

    @BeforeEach
    void setUp() {
        service = new ReportingService(queryRepository, companyService);
    }

    @Test
    void exportsAllStatementPagesWithTheExistingReportQuery() {
        UUID companyId = UUID.randomUUID();
        UUID currentAccountId = UUID.randomUUID();
        LocalDate from = LocalDate.of(2026, 7, 1);
        LocalDate to = LocalDate.of(2026, 7, 31);
        CurrentAccountStatementRow first = row("DEVIR-1");
        CurrentAccountStatementRow second = row("SF-2026-000001");
        when(queryRepository.currentAccountStatement(
            companyId,
            currentAccountId,
            from,
            to,
            PageRequest.of(0, 100)
        )).thenReturn(new PageImpl<>(List.of(first), PageRequest.of(0, 100), 101));
        when(queryRepository.currentAccountStatement(
            companyId,
            currentAccountId,
            from,
            to,
            PageRequest.of(1, 100)
        )).thenReturn(new PageImpl<>(List.of(second), PageRequest.of(1, 100), 101));

        assertThat(service.currentAccountStatementForExport(
            companyId,
            currentAccountId,
            from,
            to
        )).containsExactly(
            CurrentAccountStatementExportRow.from(first),
            CurrentAccountStatementExportRow.from(second)
        );
        verify(queryRepository).currentAccountStatement(
            companyId,
            currentAccountId,
            from,
            to,
            PageRequest.of(0, 100)
        );
        verify(queryRepository).currentAccountStatement(
            companyId,
            currentAccountId,
            from,
            to,
            PageRequest.of(1, 100)
        );
    }

    private CurrentAccountStatementRow row(String voucherNumber) {
        return new CurrentAccountStatementRow(
            UUID.randomUUID(),
            voucherNumber,
            "OPENING_BALANCE",
            LocalDate.of(2026, 7, 1),
            null,
            "Statement row",
            new BigDecimal("100.0000"),
            BigDecimal.ZERO,
            new BigDecimal("100.0000"),
            "TRY",
            BigDecimal.ONE
        );
    }
}
