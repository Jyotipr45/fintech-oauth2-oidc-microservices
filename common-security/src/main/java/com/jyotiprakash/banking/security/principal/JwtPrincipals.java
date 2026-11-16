/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.principal;

import java.util.Optional;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.jyotiprakash.banking.security.authority.KeycloakGrantedAuthoritiesConverter;

/**
 * Accessors for the authenticated, signature-verified JWT of the current (servlet) request.
 *
 * <p>Ownership decisions must use {@link #currentSubject()} &mdash; the {@code sub} claim of a token
 * whose signature, issuer and audience have already been verified &mdash; and never an owner id taken
 * from the request path, query or body.</p>
 *
 * @author Jyotiprakash Parida
 */
public final class JwtPrincipals {

    private JwtPrincipals() {
    }

    public static Optional<Jwt> currentJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            return Optional.of(jwtAuthentication.getToken());
        }
        return Optional.empty();
    }

    /**
     * @return the verified {@code sub} claim of the current request
     * @throws AuthenticationCredentialsNotFoundException when the request is not JWT-authenticated
     */
    public static String currentSubject() {
        return currentJwt()
                .map(Jwt::getSubject)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException(
                        "No JWT-authenticated principal in the security context"));
    }

    public static boolean hasRole(Authentication authentication, String role) {
        return hasAuthority(authentication, KeycloakGrantedAuthoritiesConverter.ROLE_PREFIX + role);
    }

    public static boolean hasAuthority(Authentication authentication, String authority) {
        if (authentication == null) {
            return false;
        }
        for (GrantedAuthority granted : authentication.getAuthorities()) {
            if (authority.equals(granted.getAuthority())) {
                return true;
            }
        }
        return false;
    }
}
