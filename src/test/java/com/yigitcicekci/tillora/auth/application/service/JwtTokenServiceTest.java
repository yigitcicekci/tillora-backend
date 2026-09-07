package com.yigitcicekci.tillora.auth.application.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yigitcicekci.tillora.shared.error.BusinessException;
import com.yigitcicekci.tillora.user.application.service.AuthenticatedUserInfo;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class JwtTokenServiceTest {

    private static final String SECRET = "test-only-jwt-secret-at-least-32-characters";

    private final JwtTokenService jwtTokenService = new JwtTokenService(
        SECRET,
        Duration.ofMinutes(15),
        Duration.ofDays(7)
    );

    @Test
    void rejectsTokensCreatedBeforePasswordChange() {
        UUID companyId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        AuthenticatedUserInfo initialUser = user(userId, companyId, null);
        JwtTokenPair initialPair = jwtTokenService.createTokenPair(initialUser, UUID.randomUUID());
        VerifiedJwtToken initialToken = jwtTokenService.verifyAccessToken(initialPair.accessToken());

        assertThatCode(() -> jwtTokenService.verifyUserState(initialToken, initialUser)).doesNotThrowAnyException();

        Instant changedAt = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        AuthenticatedUserInfo changedUser = user(userId, companyId, changedAt);

        assertThatThrownBy(() -> jwtTokenService.verifyUserState(initialToken, changedUser))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("TOKEN_REVOKED");

        JwtTokenPair replacementPair = jwtTokenService.createTokenPair(changedUser, UUID.randomUUID());
        VerifiedJwtToken replacementToken = jwtTokenService.verifyAccessToken(replacementPair.accessToken());
        assertThatCode(() -> jwtTokenService.verifyUserState(replacementToken, changedUser)).doesNotThrowAnyException();
    }

    @Test
    void rejectsTokensForAnotherIssuerAudienceOrFutureValidityWindow() {
        AuthenticatedUserInfo user = user(UUID.randomUUID(), UUID.randomUUID(), null);
        String token = jwtTokenService.createTokenPair(user, UUID.randomUUID()).accessToken();

        assertInvalid(withClaim(token, "iss", "other-service"));
        assertInvalid(withClaim(token, "aud", "other-api"));
        assertInvalid(withClaim(token, "nbf", Instant.now().plus(Duration.ofMinutes(5)).getEpochSecond()));
        assertInvalid(withClaim(token, "iat", Instant.now().plus(Duration.ofMinutes(5)).getEpochSecond()));
    }

    @Test
    void rejectsBlankAndShortSigningSecrets() {
        assertThatThrownBy(() -> new JwtTokenService(
            " ".repeat(32),
            Duration.ofMinutes(15),
            Duration.ofDays(7)
        )).isInstanceOf(IllegalStateException.class);

        assertThatThrownBy(() -> new JwtTokenService(
            "a".repeat(31),
            Duration.ofMinutes(15),
            Duration.ofDays(7)
        )).isInstanceOf(IllegalStateException.class);
    }

    private void assertInvalid(String token) {
        assertThatThrownBy(() -> jwtTokenService.verifyAccessToken(token))
            .isInstanceOf(BusinessException.class)
            .extracting(exception -> ((BusinessException) exception).code())
            .isEqualTo("INVALID_TOKEN");
    }

    private String withClaim(String token, String claim, Object value) {
        try {
            String[] parts = token.split("\\.");
            Base64.Decoder decoder = Base64.getUrlDecoder();
            Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
            ObjectMapper objectMapper = new ObjectMapper();
            Map<String, Object> payload = objectMapper.readValue(
                decoder.decode(parts[1]),
                new TypeReference<>() {
                }
            );
            payload.put(claim, value);
            String unsigned = parts[0] + "." + encoder.encodeToString(objectMapper.writeValueAsBytes(payload));
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return unsigned + "." + encoder.encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private AuthenticatedUserInfo user(UUID userId, UUID companyId, Instant passwordChangedAt) {
        return new AuthenticatedUserInfo(
            userId,
            companyId,
            "user",
            "user@example.com",
            "hash",
            "Test",
            "User",
            true,
            passwordChangedAt == null,
            passwordChangedAt,
            Set.of("ADMIN"),
            Set.of("USER_READ")
        );
    }
}
