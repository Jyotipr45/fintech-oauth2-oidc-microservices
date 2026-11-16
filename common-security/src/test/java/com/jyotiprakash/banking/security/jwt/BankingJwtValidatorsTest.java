/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import com.jyotiprakash.banking.security.config.BankingSecurityProperties;

/**
 * Exercises the claim-validation chain applied after signature verification.
 *
 * @author Jyotiprakash Parida
 */
class BankingJwtValidatorsTest {

    private static final String ISSUER = "http://keycloak:8080/realms/banking-realm";

    private final OAuth2TokenValidator<Jwt> validator = BankingJwtValidators.create(properties());

    @Test
    void acceptsWellFormedAccessToken() {
        assertThat(validator.validate(validToken().build()).hasErrors()).isFalse();
    }

    @Test
    void rejectsExpiredToken() {
        Instant past = Instant.now().minus(Duration.ofMinutes(10));
        Jwt jwt = validToken().issuedAt(past.minusSeconds(300)).expiresAt(past).build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenNotYetValid() {
        Jwt jwt = validToken().notBefore(Instant.now().plus(Duration.ofMinutes(5))).build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithoutExpiry() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .issuer(ISSUER).audience(List.of("account-service")).subject("user-1").claim("typ", "Bearer")
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsForeignIssuer() {
        Jwt jwt = validToken().issuer("http://evil.example/realms/banking-realm").build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenMintedForAnotherAudience() {
        Jwt jwt = validToken().audience(List.of("some-other-api")).build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsIdTokenPresentedAsAccessToken() {
        Jwt jwt = validToken().claim("typ", "ID").build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void rejectsTokenWithoutSubject() {
        Instant now = Instant.now();
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256")
                .issuer(ISSUER).audience(List.of("account-service")).claim("typ", "Bearer")
                .issuedAt(now).expiresAt(now.plusSeconds(300))
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void refusesToStartWithoutAudiences() {
        BankingSecurityProperties properties = properties();
        properties.setAudiences(List.of());
        assertThatIllegalArgumentException().isThrownBy(() -> BankingJwtValidators.create(properties));
    }

    @Test
    void jwksCacheUsesConfiguredName() {
        assertThat(ServletJwtDecoders.jwksCache(Duration.ofMinutes(5)).getName())
                .isEqualTo(ServletJwtDecoders.JWKS_CACHE_NAME);
    }

    private static Jwt.Builder validToken() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .header("kid", "rsa-key-1")
                .issuer(ISSUER)
                .audience(List.of("account-service", "banking-api"))
                .subject("8f14e45f-ceea-467a-9575-3c3a1c0b6f11")
                .claim("typ", "Bearer")
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plusSeconds(300));
    }

    private static BankingSecurityProperties properties() {
        BankingSecurityProperties properties = new BankingSecurityProperties();
        properties.setIssuerUri(ISSUER);
        properties.setAudiences(List.of("account-service"));
        return properties;
    }
}
