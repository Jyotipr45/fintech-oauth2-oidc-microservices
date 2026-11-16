/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.jwt;

import java.time.Duration;

import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;

import com.jyotiprakash.banking.security.config.BankingSecurityProperties;

import io.netty.channel.ChannelOption;
import reactor.netty.http.client.HttpClient;

/**
 * Factory for the RS256-only, Keycloak-backed {@link ReactiveJwtDecoder} used by the API gateway.
 * Same trust model as {@link ServletJwtDecoders}; the reactive JWKS source caches the key set and
 * re-fetches it when a token arrives with an unknown {@code kid}.
 *
 * @author Jyotiprakash Parida
 */
public final class ReactiveJwtDecoders {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration RESPONSE_TIMEOUT = Duration.ofSeconds(5);

    private ReactiveJwtDecoders() {
    }

    public static ReactiveJwtDecoder create(BankingSecurityProperties properties) {
        NimbusReactiveJwtDecoder.JwkSetUriReactiveJwtDecoderBuilder builder =
                StringUtils.hasText(properties.getJwkSetUri())
                        ? NimbusReactiveJwtDecoder.withJwkSetUri(properties.getJwkSetUri())
                        : NimbusReactiveJwtDecoder.withIssuerLocation(properties.getIssuerUri());

        NimbusReactiveJwtDecoder decoder = builder
                .jwsAlgorithm(SignatureAlgorithm.RS256)
                .webClient(boundedWebClient())
                .build();
        decoder.setJwtValidator(BankingJwtValidators.create(properties));
        return decoder;
    }

    private static WebClient boundedWebClient() {
        HttpClient httpClient = HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(CONNECT_TIMEOUT.toMillis()))
                .responseTimeout(RESPONSE_TIMEOUT);
        return WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }
}
