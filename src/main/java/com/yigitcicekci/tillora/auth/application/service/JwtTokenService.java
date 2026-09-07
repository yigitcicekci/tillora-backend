package com.yigitcicekci.tillora.auth.application.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.AuthenticatedUserInfo;
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
public class JwtTokenService {

    private static final String HMAC_ALGORITHM = "HmacSHA256";
    private static final int MIN_SECRET_BYTES = 32;
    private static final String ISSUER = "tillora-backend";
    private static final String AUDIENCE = "tillora-api";
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);
    private static final Base64.Encoder BASE64_URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder BASE64_URL_DECODER = Base64.getUrlDecoder();

    private final ObjectMapper objectMapper;
    private final String secret;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;

    public JwtTokenService(
        @Value("${tillora.security.jwt.secret}") String secret,
        @Value("${tillora.security.jwt.access-token-ttl}") Duration accessTokenTtl,
        @Value("${tillora.security.jwt.refresh-token-ttl}") Duration refreshTokenTtl
    ) {
        if (secret == null || secret.isBlank() || secret.chars().anyMatch(Character::isWhitespace)
            || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("JWT secret must be at least 32 non-whitespace bytes.");
        }
        this.objectMapper = new ObjectMapper();
        this.secret = secret;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public JwtTokenPair createTokenPair(AuthenticatedUserInfo user, UUID refreshTokenId) {
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plus(accessTokenTtl);
        Instant refreshExpiresAt = now.plus(refreshTokenTtl);
        String accessToken = createToken(UUID.randomUUID(), user, "access", accessExpiresAt);
        String refreshToken = createToken(refreshTokenId, user, "refresh", refreshExpiresAt);
        return new JwtTokenPair(accessToken, refreshToken, accessExpiresAt, refreshExpiresAt);
    }

    public VerifiedJwtToken verifyAccessToken(String token) {
        VerifiedJwtToken verified = verify(token);
        if (!"access".equals(verified.type())) {
            throw invalidToken("INVALID_TOKEN");
        }
        return verified;
    }

    public VerifiedJwtToken verifyRefreshToken(String token) {
        VerifiedJwtToken verified = verify(token);
        if (!"refresh".equals(verified.type())) {
            throw invalidToken("INVALID_REFRESH_TOKEN");
        }
        return verified;
    }

    public String tokenHash(String token) {
        return hex(hmac(token));
    }

    public void verifyUserState(VerifiedJwtToken token, AuthenticatedUserInfo user) {
        if (!token.userId().equals(user.id()) || !token.companyId().equals(user.companyId())) {
            throw invalidToken("INVALID_TOKEN");
        }
        if (token.credentialVersion() != credentialVersion(user.passwordChangedAt())) {
            throw invalidToken("TOKEN_REVOKED");
        }
    }

    private String createToken(UUID tokenId, AuthenticatedUserInfo user, String type, Instant expiresAt) {
        Instant issuedAt = Instant.now();
        Map<String, Object> header = new LinkedHashMap<>();
        header.put("alg", "HS256");
        header.put("typ", "JWT");
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("jti", tokenId.toString());
        payload.put("sub", user.id().toString());
        payload.put("companyId", user.companyId().toString());
        payload.put("username", user.username());
        payload.put("authorities", user.authorities().stream().sorted().toList());
        payload.put("credentialVersion", credentialVersion(user.passwordChangedAt()));
        payload.put("type", type);
        payload.put("iss", ISSUER);
        payload.put("aud", AUDIENCE);
        payload.put("exp", expiresAt.getEpochSecond());
        payload.put("nbf", issuedAt.getEpochSecond());
        payload.put("iat", issuedAt.getEpochSecond());
        String unsignedToken = encodeJson(header) + "." + encodeJson(payload);
        return unsignedToken + "." + encode(hmac(unsignedToken));
    }

    private VerifiedJwtToken verify(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw invalidToken("INVALID_TOKEN");
        }
        String unsignedToken = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(hmac(unsignedToken), decodeSignature(parts[2]))) {
            throw invalidToken("INVALID_TOKEN");
        }
        try {
            Map<String, Object> payload = decodePayload(parts[1]);
            Instant now = Instant.now();
            Instant expiresAt = Instant.ofEpochSecond(number(payload.get("exp")));
            Instant notBefore = Instant.ofEpochSecond(number(payload.get("nbf")));
            Instant issuedAt = Instant.ofEpochSecond(number(payload.get("iat")));
            if (!ISSUER.equals(string(payload.get("iss")))
                || !AUDIENCE.equals(string(payload.get("aud")))
                || notBefore.isAfter(now.plus(CLOCK_SKEW))
                || issuedAt.isAfter(now.plus(CLOCK_SKEW))
                || issuedAt.isAfter(expiresAt)) {
                throw invalidToken("INVALID_TOKEN");
            }
            if (!expiresAt.isAfter(now)) {
                throw invalidToken("TOKEN_EXPIRED");
            }
            return new VerifiedJwtToken(
                UUID.fromString(string(payload.get("jti"))),
                UUID.fromString(string(payload.get("sub"))),
                UUID.fromString(string(payload.get("companyId"))),
                string(payload.get("username")),
                authorities(payload.get("authorities")),
                optionalNumber(payload.get("credentialVersion")),
                string(payload.get("type")),
                expiresAt
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidToken("INVALID_TOKEN");
        }
    }

    private String encodeJson(Map<String, Object> value) {
        try {
            return encode(objectMapper.writeValueAsBytes(value));
        } catch (Exception exception) {
            throw new BusinessException("TOKEN_CREATION_FAILED", "Token creation failed.");
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

    private long optionalNumber(Object value) {
        return value == null ? 0 : number(value);
    }

    private long credentialVersion(Instant passwordChangedAt) {
        return passwordChangedAt == null ? 0 : passwordChangedAt.toEpochMilli();
    }

    private Set<String> authorities(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().map(Object::toString).collect(Collectors.toSet());
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
            case "TOKEN_REVOKED" -> "Token revoked.";
            default -> "Invalid token.";
        };
        return new BusinessException(code, message, HttpStatus.UNAUTHORIZED);
    }
}
