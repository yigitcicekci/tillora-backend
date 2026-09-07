package com.yigitcicekci.tillora.dashboard.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.company.application.service.CompanyFinancialContext;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardAlerts;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardCards;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardSummary;
import com.yigitcicekci.tillora.dashboard.infrastructure.cache.DashboardCache;
import com.yigitcicekci.tillora.dashboard.infrastructure.persistence.DashboardQueryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DashboardServiceTest {

    private final CompanyService companyService = mock(CompanyService.class);
    private final DashboardQueryRepository queryRepository = mock(DashboardQueryRepository.class);
    private final DashboardCache cache = mock(DashboardCache.class);
    private DashboardService service;

    @BeforeEach
    void setUp() {
        service = new DashboardService(companyService, queryRepository, cache);
    }

    @Test
    void returnsTenantAndPeriodScopedCachedSummary() {
        UUID companyId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 7);
        DashboardSummary cached = summary(period);
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(cache.get(companyId, period)).thenReturn(Optional.of(cached));

        assertThat(service.summary(companyId, period)).isEqualTo(cached);
        verify(queryRepository, never()).summary(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    @Test
    void loadsPostgresqlAndCachesWhenRedisHasNoValue() {
        UUID companyId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 7);
        DashboardSummary loaded = summary(period);
        when(companyService.financialContext(companyId))
            .thenReturn(new CompanyFinancialContext("TRY", "Europe/Istanbul"));
        when(cache.get(companyId, period)).thenReturn(Optional.empty());
        when(queryRepository.summary(
            org.mockito.ArgumentMatchers.eq(companyId),
            org.mockito.ArgumentMatchers.eq(period),
            org.mockito.ArgumentMatchers.eq("TRY"),
            org.mockito.ArgumentMatchers.any(LocalDate.class)
        )).thenReturn(loaded);

        assertThat(service.summary(companyId, period)).isEqualTo(loaded);
        verify(cache).put(companyId, period, loaded);
    }

    private DashboardSummary summary(YearMonth period) {
        BigDecimal zero = new BigDecimal("0.0000");
        return new DashboardSummary(
            period,
            "TRY",
            new DashboardCards(zero, zero, zero, zero, zero, zero, zero, zero, zero),
            new DashboardAlerts(0, 0),
            Instant.now()
        );
    }
}
