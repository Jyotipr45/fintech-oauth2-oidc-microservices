/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

/**
 * @author Jyotiprakash Parida
 */
class GatewayConfigTest {

    @Test
    void corsUsesExplicitOriginsWithoutCredentials() {
        GatewayCorsProperties properties = new GatewayCorsProperties();
        properties.setAllowedOrigins(List.of("http://localhost:3000"));

        CorsConfiguration configuration = CorsConfig.buildConfiguration(properties);

        assertThat(configuration.getAllowedOrigins()).containsExactly("http://localhost:3000");
        assertThat(configuration.getAllowCredentials()).isFalse();
        assertThat(configuration.getAllowedHeaders()).contains("Authorization", "Idempotency-Key");
    }

    @Test
    void corsRejectsWildcardAndEmptyOrigins() {
        GatewayCorsProperties wildcard = new GatewayCorsProperties();
        wildcard.setAllowedOrigins(List.of("*"));
        assertThatIllegalStateException().isThrownBy(() -> CorsConfig.buildConfiguration(wildcard));

        GatewayCorsProperties empty = new GatewayCorsProperties();
        assertThatIllegalStateException().isThrownBy(() -> CorsConfig.buildConfiguration(empty));
    }

    @Test
    void rateLimitKeyIsTheAuthenticatedSubject() {
        Principal principal = () -> "8f14e45f-ceea-467a-9575-3c3a1c0b6f11";
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/api/v1/accounts"))
                .mutate().principal(Mono.just(principal)).build();

        StepVerifier.create(new RateLimitConfig().subjectKeyResolver().resolve(exchange))
                .expectNext("sub:8f14e45f-ceea-467a-9575-3c3a1c0b6f11")
                .verifyComplete();
    }

    @Test
    void rateLimitKeyFallsBackToPeerAddress() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/actuator/health")
                .remoteAddress(new InetSocketAddress("10.1.2.3", 51515)));

        StepVerifier.create(new RateLimitConfig().subjectKeyResolver().resolve(exchange))
                .expectNext("ip:10.1.2.3")
                .verifyComplete();
    }
}
