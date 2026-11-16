/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.gateway.config;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

import com.jyotiprakash.banking.security.logging.CorrelationIds;

/**
 * Explicit-origin CORS policy enforced at the edge.
 *
 * <p>{@code allowCredentials} is {@code false}: the SPA sends the access token in the
 * {@code Authorization} header, never in cookies, so the browser must not attach ambient credentials.</p>
 *
 * @author Jyotiprakash Parida
 */
@Configuration(proxyBeanMethods = false)
public class CorsConfig {

    static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    @Bean
    public CorsConfigurationSource corsConfigurationSource(GatewayCorsProperties properties) {
        CorsConfiguration configuration = buildConfiguration(properties);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }

    static CorsConfiguration buildConfiguration(GatewayCorsProperties properties) {
        List<String> origins = properties.getAllowedOrigins().stream()
                .map(String::trim)
                .filter(StringUtils::hasText)
                .toList();
        if (origins.isEmpty()) {
            throw new IllegalStateException("gateway.cors.allowed-origins must list at least one explicit origin");
        }
        for (String origin : origins) {
            if (origin.contains("*")) {
                throw new IllegalStateException("Wildcard CORS origins are not allowed: " + origin);
            }
        }

        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(
                HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, HttpHeaders.ACCEPT,
                CorrelationIds.HEADER, IDEMPOTENCY_KEY_HEADER));
        configuration.setExposedHeaders(List.of(
                CorrelationIds.HEADER, HttpHeaders.WWW_AUTHENTICATE,
                "X-RateLimit-Remaining", "X-RateLimit-Replenish-Rate", "X-RateLimit-Burst-Capacity"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(properties.getMaxAge());
        return configuration;
    }
}
