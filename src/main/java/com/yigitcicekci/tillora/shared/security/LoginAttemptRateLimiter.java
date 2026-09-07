package com.yigitcicekci.tillora.shared.security;

import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class LoginAttemptRateLimiter {

    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>(
        "local current = redis.call('INCR', KEYS[1]); if current == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]); end; return current;",
        Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final int maxAttempts;
    private final Duration window;

    public LoginAttemptRateLimiter(
        StringRedisTemplate redisTemplate,
        @Value("${tillora.security.login-rate-limit.max-attempts:10}") int maxAttempts,
        @Value("${tillora.security.login-rate-limit.window:1m}") Duration window
    ) {
        this.redisTemplate = redisTemplate;
        this.maxAttempts = maxAttempts;
        this.window = window;
    }

    public void check(String namespace, String identifier, String remoteAddress) {
        try {
            long ipAttempts = increment("rate-limit:" + namespace + ":ip:" + hash(remoteAddress));
            long accountAttempts = increment(
                "rate-limit:" + namespace + ":account:" + hash(identifier.trim().toLowerCase(Locale.ROOT))
            );
            if (ipAttempts > maxAttempts || accountAttempts > maxAttempts) {
                throw new BusinessException("LOGIN_RATE_LIMIT_EXCEEDED", "Too many login attempts.", HttpStatus.TOO_MANY_REQUESTS);
            }
        } catch (DataAccessException exception) {
            throw new BusinessException("LOGIN_RATE_LIMIT_UNAVAILABLE", "Login is temporarily unavailable.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private long increment(String key) {
        Long value = redisTemplate.execute(INCREMENT_SCRIPT, List.of(key), String.valueOf(window.toMillis()));
        if (value == null) {
            throw new BusinessException("LOGIN_RATE_LIMIT_UNAVAILABLE", "Login is temporarily unavailable.", HttpStatus.SERVICE_UNAVAILABLE);
        }
        return value;
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
