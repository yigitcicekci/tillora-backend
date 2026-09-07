package com.yigitcicekci.tillora.shared.observability;

import com.yigitcicekci.tillora.shared.error.BusinessException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Aspect
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class BusinessMetricsAspect {

    private final MeterRegistry registry;
    private final long slowQueryNanos;

    public BusinessMetricsAspect(
        MeterRegistry registry,
        @Value("${tillora.observability.slow-query-threshold:500ms}") Duration slowQueryThreshold
    ) {
        if (slowQueryThreshold == null || slowQueryThreshold.isNegative() || slowQueryThreshold.isZero()) {
            throw new IllegalStateException("Slow query threshold must be positive.");
        }
        this.registry = registry;
        this.slowQueryNanos = slowQueryThreshold.toNanos();
    }

    @Around("execution(* com.yigitcicekci.tillora.auth.application.service.AuthenticationService.login(..))")
    public Object login(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        try {
            Object result = joinPoint.proceed();
            counter("tillora.login.attempts", "outcome", "success").increment();
            return result;
        } catch (Throwable throwable) {
            counter("tillora.login.attempts", "outcome", "failure").increment();
            throw throwable;
        } finally {
            timer("tillora.login.duration", "operation", "login")
                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    @Around("execution(* com.yigitcicekci.tillora.voucher.application.service.VoucherService.create(..))"
        + " || execution(* com.yigitcicekci.tillora.voucher.application.service.CollectionVoucherService.create(..))"
        + " || execution(* com.yigitcicekci.tillora.voucher.application.service.PaymentVoucherService.create(..))"
        + " || execution(* com.yigitcicekci.tillora.voucher.application.service.TransferVoucherService.create(..))")
    public Object voucherCreation(ProceedingJoinPoint joinPoint) throws Throwable {
        return timed(joinPoint, "tillora.voucher.creation", "create");
    }

    @Around("execution(* com.yigitcicekci.tillora.invoice.application.service.InvoiceService.approve(..))")
    public Object invoiceApproval(ProceedingJoinPoint joinPoint) throws Throwable {
        return timed(joinPoint, "tillora.invoice.approval", "approve");
    }

    @AfterReturning(
        pointcut = "execution(* com.yigitcicekci.tillora.dashboard.infrastructure.cache.DashboardCache.get(..))",
        returning = "result"
    )
    public void dashboardCache(Optional<?> result) {
        counter("tillora.dashboard.cache.requests", "result", result.isPresent() ? "hit" : "miss")
            .increment();
    }

    @Around("execution(* com.yigitcicekci.tillora..infrastructure.persistence..*.*(..))"
        + " || execution(* com.yigitcicekci.tillora..domain.repository..*.*(..))")
    public Object databaseQuery(ProceedingJoinPoint joinPoint) throws Throwable {
        long started = System.nanoTime();
        try {
            return joinPoint.proceed();
        } finally {
            long duration = System.nanoTime() - started;
            String repository = repositoryName(joinPoint.getTarget().getClass().getSimpleName());
            String method = joinPoint.getSignature().getName();
            timer("tillora.database.query", "repository", repository, "method", method)
                .record(duration, TimeUnit.NANOSECONDS);
            if (duration >= slowQueryNanos) {
                counter("tillora.database.slow.queries", "repository", repository, "method", method)
                    .increment();
            }
        }
    }

    @AfterThrowing(
        pointcut = "execution(* com.yigitcicekci.tillora..api.controller..*.*(..))",
        throwing = "exception"
    )
    public void businessFailure(BusinessException exception) {
        if (duplicate(exception.code())) {
            counter("tillora.duplicate.prevented", "code", exception.code()).increment();
        }
    }

    private Object timed(ProceedingJoinPoint joinPoint, String name, String operation) throws Throwable {
        long started = System.nanoTime();
        String outcome = "success";
        try {
            return joinPoint.proceed();
        } catch (Throwable throwable) {
            outcome = "failure";
            throw throwable;
        } finally {
            timer(name, "operation", operation, "outcome", outcome)
                .record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
        }
    }

    private boolean duplicate(String code) {
        return code.equals("IDEMPOTENCY_KEY_REUSED")
            || code.contains("ALREADY_EXISTS")
            || code.startsWith("DUPLICATE_");
    }

    private String repositoryName(String name) {
        int proxyMarker = name.indexOf("$$");
        return proxyMarker < 0 ? name : name.substring(0, proxyMarker);
    }

    private Counter counter(String name, String... tags) {
        return Counter.builder(name).tags(tags).register(registry);
    }

    private Timer timer(String name, String... tags) {
        return Timer.builder(name).tags(tags).publishPercentileHistogram().register(registry);
    }
}
