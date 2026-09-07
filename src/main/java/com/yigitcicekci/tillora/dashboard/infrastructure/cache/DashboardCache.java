package com.yigitcicekci.tillora.dashboard.infrastructure.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yigitcicekci.tillora.dashboard.api.response.DashboardSummary;
import java.time.Duration;
import java.time.YearMonth;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

@Component
public class DashboardCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(DashboardCache.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    public DashboardCache(
        StringRedisTemplate redisTemplate,
        @Value("${tillora.dashboard.cache-ttl:60s}") Duration ttl
    ) {
        if (ttl == null
            || ttl.compareTo(Duration.ofSeconds(30)) < 0
            || ttl.compareTo(Duration.ofMinutes(2)) > 0) {
            throw new IllegalStateException("Dashboard cache TTL must be between 30 seconds and 2 minutes.");
        }
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
        this.ttl = ttl;
    }

    public Optional<DashboardSummary> get(UUID companyId, YearMonth period) {
        try {
            String value = redisTemplate.opsForValue().get(key(companyId, period));
            return value == null
                ? Optional.empty()
                : Optional.of(objectMapper.readValue(value, DashboardSummary.class));
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn(
                "Dashboard cache read failed for companyId={} error={}",
                companyId,
                exception.getClass().getSimpleName()
            );
            return Optional.empty();
        }
    }

    public void put(UUID companyId, YearMonth period, DashboardSummary summary) {
        try {
            redisTemplate.opsForValue().set(
                key(companyId, period),
                objectMapper.writeValueAsString(summary),
                ttl
            );
        } catch (DataAccessException | JsonProcessingException exception) {
            LOGGER.warn(
                "Dashboard cache write failed for companyId={} error={}",
                companyId,
                exception.getClass().getSimpleName()
            );
        }
    }

    private String key(UUID companyId, YearMonth period) {
        return "dashboard:summary:" + companyId + ":" + period;
    }
}
