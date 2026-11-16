/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.context.runner.ReactiveWebApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.ReactiveJwtAuthenticationConverterAdapter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;

/**
 * Verifies that the right beans are contributed per web stack and that 401/403 bodies are RFC 7807.
 * A JWKS URI is configured so that no network call to Keycloak is attempted.
 *
 * @author Jyotiprakash Parida
 */
class BankingSecurityAutoConfigurationTest {

    private static final String[] PROPERTIES = {
            "banking.security.issuer-uri=http://keycloak:8080/realms/banking-realm",
            "banking.security.jwk-set-uri=http://keycloak:8080/realms/banking-realm/protocol/openid-connect/certs",
            "banking.security.audiences=account-service"
    };

    private final AutoConfigurations autoConfigurations =
            AutoConfigurations.of(JacksonAutoConfiguration.class, BankingSecurityAutoConfiguration.class);

    @Test
    void servletStackGetsDecoderConverterAndProblemDetailHandlers() {
        new WebApplicationContextRunner()
                .withConfiguration(autoConfigurations)
                .withPropertyValues(PROPERTIES)
                .run(context -> {
                    assertThat(context).hasSingleBean(JwtDecoder.class);
                    assertThat(context).hasSingleBean(JwtAuthenticationConverter.class);
                    assertThat(context).doesNotHaveBean(ReactiveJwtDecoder.class);

                    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/accounts");
                    MockHttpServletResponse response = new MockHttpServletResponse();
                    context.getBean(AuthenticationEntryPoint.class).commence(request, response,
                            new InsufficientAuthenticationException("Full authentication is required"));

                    assertThat(response.getStatus()).isEqualTo(401);
                    assertThat(response.getContentType()).isEqualTo("application/problem+json");
                    assertThat(response.getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer realm=\"banking\"");
                    assertThat(response.getContentAsString(StandardCharsets.UTF_8))
                            .contains("\"status\":401")
                            .contains("\"instance\":\"/api/v1/accounts\"");

                    MockHttpServletResponse forbidden = new MockHttpServletResponse();
                    context.getBean(AccessDeniedHandler.class).handle(request, forbidden,
                            new AccessDeniedException("Access Denied"));
                    assertThat(forbidden.getStatus()).isEqualTo(403);
                    assertThat(forbidden.getContentAsString(StandardCharsets.UTF_8))
                            .contains("\"errorCode\":\"insufficient_scope\"");
                });
    }

    @Test
    void reactiveStackGetsReactiveBeans() {
        new ReactiveWebApplicationContextRunner()
                .withConfiguration(autoConfigurations)
                .withPropertyValues(PROPERTIES)
                .run(context -> {
                    assertThat(context).hasSingleBean(ReactiveJwtDecoder.class);
                    assertThat(context).hasSingleBean(ReactiveJwtAuthenticationConverterAdapter.class);
                    assertThat(context).hasSingleBean(ServerAuthenticationEntryPoint.class);
                    assertThat(context).doesNotHaveBean(JwtDecoder.class);
                });
    }

    @Test
    void failsFastWhenAudienceIsMissing() {
        new WebApplicationContextRunner()
                .withConfiguration(autoConfigurations)
                .withPropertyValues("banking.security.issuer-uri=http://keycloak:8080/realms/banking-realm",
                        "banking.security.jwk-set-uri=http://keycloak:8080/certs")
                .run(context -> assertThat(context).hasFailed());
    }
}
