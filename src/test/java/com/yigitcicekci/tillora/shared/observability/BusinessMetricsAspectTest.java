package com.yigitcicekci.tillora.shared.observability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.yigitcicekci.tillora.shared.error.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.Optional;
import org.aspectj.lang.ProceedingJoinPoint;
import org.junit.jupiter.api.Test;

class BusinessMetricsAspectTest {

    @Test
    void recordsLoginCacheAndConflictMetrics() throws Throwable {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BusinessMetricsAspect aspect = new BusinessMetricsAspect(registry, Duration.ofMillis(10));
        ProceedingJoinPoint login = mock(ProceedingJoinPoint.class);
        when(login.proceed()).thenThrow(new BusinessException("INVALID_CREDENTIALS", "Invalid"));

        assertThatThrownBy(() -> aspect.login(login)).isInstanceOf(BusinessException.class);
        aspect.dashboardCache(Optional.of("summary"));
        aspect.businessFailure(new BusinessException("IDEMPOTENCY_KEY_REUSED", "Duplicate"));

        assertThat(registry.get("tillora.login.attempts").tag("outcome", "failure").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("tillora.dashboard.cache.requests").tag("result", "hit").counter().count())
            .isEqualTo(1);
        assertThat(registry.get("tillora.duplicate.prevented").counter().count()).isEqualTo(1);
    }
}
