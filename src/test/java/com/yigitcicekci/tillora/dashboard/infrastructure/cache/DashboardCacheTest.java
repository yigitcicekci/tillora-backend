package com.yigitcicekci.tillora.dashboard.infrastructure.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardAlerts;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardCards;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardSummary;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.YearMonth;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class DashboardCacheTest {

    @Test
    void readsAndWritesPeriodScopedSummary() throws Exception {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        DashboardCache cache = new DashboardCache(redisTemplate, Duration.ofSeconds(60));
        UUID companyId = UUID.randomUUID();
        YearMonth period = YearMonth.of(2026, 7);
        DashboardSummary summary = summary(period);
        String key = "dashboard:summary:" + companyId + ":" + period;
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(summary);
        when(values.get(key)).thenReturn(json);

        assertThat(cache.get(companyId, period)).contains(summary);
        cache.put(companyId, period, summary);

        verify(values).set(
            org.mockito.ArgumentMatchers.eq(key),
            org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.eq(Duration.ofSeconds(60))
        );
    }

    @Test
    void treatsRedisFailureAsCacheMiss() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        when(values.get(org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new RedisConnectionFailureException("Unavailable"));
        DashboardCache cache = new DashboardCache(redisTemplate, Duration.ofSeconds(60));

        assertThat(cache.get(UUID.randomUUID(), YearMonth.of(2026, 7))).isEmpty();
    }

    private DashboardSummary summary(YearMonth period) {
        BigDecimal zero = new BigDecimal("0.0000");
        return new DashboardSummary(
            period,
            "TRY",
            new DashboardCards(zero, zero, zero, zero, zero, zero, zero, zero, zero),
            new DashboardAlerts(0, 0),
            Instant.parse("2026-07-20T10:00:00Z")
        );
    }
}
