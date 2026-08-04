package com.dbtraining.reconx.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * TICKET-ADV072 — HS256 token issuance and verification.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "dev-secret-change-me-32-bytes-min!!";
    private static final String OTHER_SECRET = "another-secret-also-32-bytes-min!!!";
    private static final String ISSUER = "reconx";
    private static final long EXPIRATION_MINUTES = 60;

    private final JwtTokenProvider provider =
            new JwtTokenProvider(SECRET, EXPIRATION_MINUTES, ISSUER);

    @Test
    @DisplayName("generate puts the email in sub and the role in a role claim")
    void generateCarriesSubjectAndRole() {
        Claims claims = provider.parse(provider.generate("trader@db.com", "TRADER"));

        assertThat(claims.getSubject()).isEqualTo("trader@db.com");
        assertThat(claims.get("role", String.class)).isEqualTo("TRADER");
    }

    @Test
    @DisplayName("generate sets iat, exp and the configured issuer")
    void generateSetsTimestampsAndIssuer() {
        Claims claims = provider.parse(provider.generate("trader@db.com", "TRADER"));

        assertThat(claims.getIssuedAt()).isNotNull();
        assertThat(claims.getExpiration()).isNotNull();
        assertThat(claims.getIssuer()).isEqualTo(ISSUER);
        assertThat(claims.getExpiration().toInstant().getEpochSecond()
                - claims.getIssuedAt().toInstant().getEpochSecond())
                .isEqualTo(EXPIRATION_MINUTES * 60);
    }

    @Test
    @DisplayName("token is a three-part HS256 JWS")
    void tokenIsHs256Jws() {
        String token = provider.generate("trader@db.com", "TRADER");

        assertThat(token.split("\\.")).hasSize(3);

        String header = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[0]), StandardCharsets.UTF_8);
        assertThat(header).contains("HS256");
    }

    @Test
    @DisplayName("expirationSeconds reports the configured window in seconds")
    void reportsExpirationSeconds() {
        assertThat(provider.expirationSeconds()).isEqualTo(EXPIRATION_MINUTES * 60);
    }

    @Test
    @DisplayName("a token signed with another secret is rejected")
    void rejectsForeignSignature() {
        String foreign = new JwtTokenProvider(OTHER_SECRET, EXPIRATION_MINUTES, ISSUER)
                .generate("trader@db.com", "TRADER");

        assertThatThrownBy(() -> provider.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("a token from another issuer is rejected")
    void rejectsForeignIssuer() {
        String foreign = new JwtTokenProvider(SECRET, EXPIRATION_MINUTES, "somebody-else")
                .generate("trader@db.com", "TRADER");

        assertThatThrownBy(() -> provider.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("an expired token is rejected")
    void rejectsExpiredToken() {
        String expired = new JwtTokenProvider(SECRET, -1, ISSUER)
                .generate("trader@db.com", "TRADER");

        assertThatThrownBy(() -> provider.parse(expired)).isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    @DisplayName("a tampered payload is rejected")
    void rejectsTamperedPayload() {
        String[] parts = provider.generate("trader@db.com", "TRADER").split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"admin@db.com\",\"role\":\"ADMIN\",\"iss\":\"reconx\"}"
                        .getBytes(StandardCharsets.UTF_8));

        String tampered = parts[0] + "." + forgedPayload + "." + parts[2];

        assertThatThrownBy(() -> provider.parse(tampered)).isInstanceOf(JwtException.class);
    }
}
