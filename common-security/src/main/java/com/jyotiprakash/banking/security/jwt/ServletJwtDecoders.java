/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.jwt;

import java.time.Duration;

import org.springframework.cache.Cache;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.jyotiprakash.banking.security.config.BankingSecurityProperties;

/**
 * Factory for the RS256-only, Keycloak-backed {@link JwtDecoder} used by servlet resource servers.
 *
 * <h2>How signature verification works</h2>
 * <ol>
 *   <li>Keycloak signs every access token with the <b>private</b> key of the realm's active RSA key
 *       (the private key never leaves Keycloak) and puts that key's id in the JOSE header ({@code kid}).</li>
 *   <li>{@link NimbusJwtDecoder#withIssuerLocation(String)} lazily reads
 *       {@code <issuer>/.well-known/openid-configuration}, verifies that the advertised {@code issuer}
 *       equals the configured one, and follows {@code jwks_uri}
 *       ({@code /protocol/openid-connect/certs}) to download the realm's <b>public</b> keys.</li>
 *   <li>The JWKS document is cached for {@code banking.security.jwks-cache-ttl}; each token's {@code kid}
 *       selects the matching public key. Only {@code RS256} is accepted, so {@code alg=none} and
 *       HMAC/RSA key-confusion attacks are rejected before any claim is trusted.</li>
 *   <li>Claims are then checked by {@link BankingJwtValidators}.</li>
 * </ol>
 *
 * <p>Because public keys are fetched dynamically, Keycloak keys rotate with zero downtime and no redeploy:
 * publish the new key first (lower priority, so it is advertised but not yet used), wait longer than the
 * cache TTL, then raise its priority so it starts signing.</p>
 *
 * @author Jyotiprakash Parida
 */
public final class ServletJwtDecoders {

    static final String JWKS_CACHE_NAME = "banking-jwks";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final long JWKS_CACHE_MAX_ENTRIES = 16L;

    private ServletJwtDecoders() {
    }

    public static JwtDecoder create(BankingSecurityProperties properties) {
        NimbusJwtDecoder.JwkSetUriJwtDecoderBuilder builder = StringUtils.hasText(properties.getJwkSetUri())
                ? NimbusJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
                : NimbusJwtDecoder.withIssuerLocation(properties.getIssuerUri());

        NimbusJwtDecoder decoder = builder
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .restOperations(boundedRestTemplate())
                .cache(jwksCache(properties.getJwksCacheTtl()))
                .build();
        decoder.setJwtValidator(BankingJwtValidators.create(properties));
        return decoder;
    }

    static Cache jwksCache(Duration ttl) {
        return new CaffeineCache(JWKS_CACHE_NAME, Caffeine.newBuilder()
                .expireAfterWrite(ttl)
                .maximumSize(JWKS_CACHE_MAX_ENTRIES)
                .build());
    }

    private static RestTemplate boundedRestTemplate() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return new RestTemplate(requestFactory);
    }
}
