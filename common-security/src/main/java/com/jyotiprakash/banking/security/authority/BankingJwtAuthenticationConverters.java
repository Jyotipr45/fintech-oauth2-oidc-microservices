/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.authority;

import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import com.jyotiprakash.banking.security.config.BankingSecurityProperties;

/**
 * Builds the {@link JwtAuthenticationConverter} shared by all services.
 *
 * <p>The authentication name is pinned to the immutable {@code sub} claim (Keycloak's user UUID),
 * never to {@code preferred_username} or {@code email}, which are mutable, may be absent, and are PII.
 * Ownership checks (BOLA/IDOR prevention) compare resource owners against this value.</p>
 *
 * @author Jyotiprakash Parida
 */
public final class BankingJwtAuthenticationConverters {

    private BankingJwtAuthenticationConverters() {
    }

    public static JwtAuthenticationConverter create(BankingSecurityProperties properties) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setPrincipalClaimName(JwtClaimNames.SUB);
        converter.setJwtGrantedAuthoritiesConverter(new KeycloakGrantedAuthoritiesConverter(
                properties.getPermissionsClientId(), properties.getPermissionsClaim()));
        return converter;
    }
}
