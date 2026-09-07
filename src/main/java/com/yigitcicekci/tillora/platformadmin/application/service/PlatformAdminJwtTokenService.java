package com.yigitcicekci.tillora.platformadmin.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yigitcicekci.tillora.platformadmin.domain.entity.PlatformAdmin;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class PlatformAdminJwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final String ISSUER = "tillora-platform-admin";
    private static final String AUDIENCE = "tillora-admin-api";
    private static final String PRINCIPAL_TYPE = "PLATFORM_ADMIN";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final String secret;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public PlatformAdminJwtTokenService(
        @Value("${tillora.security.jwt.platform-admin-secret}") String secret,
        @Value("${tillora.security.jwt.access-token-ttl}") Duration accessTokenTtl,
        @Value("${tillora.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        if (secret == null || secret.isBlank() || secret.chars().anyMatch(Character::isWhitespace)
            || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("Platform admin JWT secret must be at least 32 non-whitespace bytes.");
        }
        this.objectMapper = new ObjectMapper();
        this.secret = secret;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public PlatformAdminTokenPair createTokenPair(PlatformAdmin admin, UUID refreshTokenId) {
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plus(accessTokenTtl);
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        String accessToken = createToken(UUID.randomUUID(), admin, "access", accessExpiresAt);
        String refreshToken = createToken(refreshTokenId, admin, "refresh", refreshExpiresAt);
        return new PlatformAdminTokenPair(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    public VerifiedPlatformAdminToken verifyAccessToken(String token) {
        return verify(token, "access", "INVALID_TOKEN");
    }

    public VerifiedPlatformAdminToken verifyRefreshToken(String token) {
        return verify(token, "refresh", "INVALID_REFRESH_TOKEN");
    }

    public String tokenHash(String token) {
        return hex(hmac(token));
    }

    private String createToken(UUID tokenId, PlatformAdmin admin, String tokenType, Instant expiresAt) {
        Instant issuedAt = Instant.now();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jti", tokenId.toString());
        payload.put("sub", admin.id().toString());
        payload.put("email", admin.email());
        payload.put("authorities", List.of("PLATFORM_ADMIN"));
        payload.put("type", PRINCIPAL_TYPE);
        payload.put("tokenType", tokenType);
        payload.put("iss", ISSUER);
        payload.put("aud", AUDIENCE);
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("nbf", issuedAt.getEpochSecond());
        payload.put("iat", issuedAt.getEpochSecond());
        String unsignedToken = encodeJson(header) + "." + encodeJson(payload);
        return unsignedToken + "." + encode(hmac(unsignedToken));
    }

    private VerifiedPlatformAdminToken verify(String token, String expectedTokenType, String invalidCode) {
        if (token == null || token.isBlank()) {
            throw invalidToken(invalidCode);
        }
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw invalidToken(invalidCode);
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(hmac(unsignedToken), decodeSignature(parts[2]))) {
            throw invalidToken(invalidCode);
        }
        try {
            Map<String, Object> payload = decodePayload(parts[1]);
            Instant now = Instant.now();
            Instant expiresAt = Instant.ofEpochSecond(number(payload.get("exp")));
            Instant notBefore = Instant.ofEpochSecond(number(payload.get("nbf")));
            Instant issuedAt = Instant.ofEpochSecond(number(payload.get("iat")));
            if (!ISSUER.equals(string(payload.get("iss")))
                || !AUDIENCE.equals(string(payload.get("aud")))
                || !PRINCIPAL_TYPE.equals(string(payload.get("type")))
                || !expectedTokenType.equals(string(payload.get("tokenType")))
                || notBefore.isAfter(now.plus(CLOCK_SKEW))
                || issuedAt.isAfter(now.plus(CLOCK_SKEW))
                || issuedAt.isAfter(expiresAt)) {
                throw invalidToken(invalidCode);
            }
            if (!expiresAt.isAfter(now)) {
                throw invalidToken(expectedTokenType.equals("refresh") ? "REFRESH_TOKEN_EXPIRED" : "TOKEN_EXPIRED");
            }
            return new VerifiedPlatformAdminToken(
                UUID.fromString(string(payload.get("jti"))),
                UUID.fromString(string(payload.get("sub"))),
                string(payload.get("email")),
                authorities(payload.get("authorities")),
                expectedTokenType,
                expiresAt
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidToken(invalidCode);
        }
    }

    private Map<String, Object> decodePayload(String value) {
        try {
            return objectMapper.readValue(BASE64_URL_DECODER.decode(value), new TypeReference<>() {
            });
        } catch (Exception exception) {
            throw invalidToken("INVALID_TOKEN");
        }
    }

    private byte[] hmac(String value) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new BusinessException("TOKEN_CREATION_FAILED", "Token creation failed.");
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return encode(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new BusinessException("TOKEN_CREATION_FAILED", "Token creation failed.");
        }
    }

    private String encode(byte[] value) {
        return BASE64_URL_ENCODER.encodeToString(value);
    }

    private String string(Object value) {
        if (value instanceof String string) {
            return string;
        }
        throw invalidToken("INVALID_TOKEN");
    }

    private long number(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        throw invalidToken("INVALID_TOKEN");
    }

    private Set<String> authorities(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toUnmodifiableSet());
        }
        throw invalidToken("INVALID_TOKEN");
    }

    private String hex(byte[] value) {
        StringBuilder builder = new StringBuilder(value.length * 2);
        for (byte b : value) {
            builder.append(String.format("%02x", b));
        }
        return builder.toString();
    }

    private byte[] decodeSignature(String value) {
        try {
            return BASE64_URL_DECODER.decode(value);
        } catch (IllegalArgumentException exception) {
            return new byte[0];
        }
    }

    private BusinessException invalidToken(String code) {
        String message = switch (code) {
            case "TOKEN_EXPIRED" -> "Token expired.";
            case "REFRESH_TOKEN_EXPIRED" -> "Refresh token expired.";
            default -> "Invalid token.";
        };
        return new BusinessException(code, message, HttpStatus.UNAUTHORIZED);
    }
}
