package com.yigitcicekci.tillora.dashboard.application.service;

import com.yigitcicekci.tillora.company.application.service.CompanyFinancialContext;
import com.yigitcicekci.tillora.company.application.service.CompanyService;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardSummary;
import com.yigitcicekci.tillora.dashboard.infrastructure.cache.DashboardCache;
import com.yigitcicekci.tillora.dashboard.infrastructure.persistence.DashboardQueryRepository;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final CompanyService companyService;
    private final DashboardQueryRepository queryRepository;
    private final DashboardCache cache;

    public DashboardService(
        CompanyService companyService,
        DashboardQueryRepository queryRepository,
        DashboardCache cache
    ) {
        this.companyService = companyService;
        this.queryRepository = queryRepository;
        this.cache = cache;
    }

    public DashboardSummary summary(UUID companyId, YearMonth requestedPeriod) {
        CompanyFinancialContext context = companyService.financialContext(companyId);
        LocalDate today = LocalDate.now(ZoneId.of(context.timezone()));
        YearMonth period = requestedPeriod == null ? YearMonth.from(today) : requestedPeriod;
        validate(period);
        return cache.get(companyId, period).orElseGet(() -> load(
            companyId,
            period,
            context.currency(),
            today
        ));
    }

    private DashboardSummary load(
        UUID companyId,
        YearMonth period,
        String currency,
        LocalDate today
    ) {
        DashboardSummary summary = queryRepository.summary(
            companyId,
            period,
            currency,
            today
        );
        cache.put(companyId, period, summary);
        return summary;
    }

    private void validate(YearMonth period) {
        if (period.getYear() < 2000 || period.getYear() > 9999) {
            throw new BusinessException(
                "DASHBOARD_PERIOD_INVALID",
                "Dashboard period is outside the supported range.",
                HttpStatus.BAD_REQUEST
            );
        }
    }
}
