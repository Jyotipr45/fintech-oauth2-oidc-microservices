/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.authority;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * @author Jyotiprakash Parida
 */
class KeycloakGrantedAuthoritiesConverterTest {

    private final KeycloakGrantedAuthoritiesConverter converter =
            new KeycloakGrantedAuthoritiesConverter("banking-api", "permissions");

    @Test
    void mapsRealmRolesClientRolesPermissionsAndScopes() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", List.of("customer", "AUDITOR")))
                .claim("resource_access", Map.of(
                        "banking-api", Map.of("roles", List.of("account:read", "transfer:create")),
                        "some-other-client", Map.of("roles", List.of("should-be-ignored"))))
                .claim("permissions", List.of("statement:download"))
                .claim("scope", "openid roles")
                .build();

        assertThat(names(converter.convert(jwt))).containsExactlyInAnyOrder(
                "ROLE_CUSTOMER", "ROLE_AUDITOR",
                "account:read", "transfer:create",
                "statement:download",
                "SCOPE_openid", "SCOPE_roles");
    }

    @Test
    void dropsMalformedValuesInsteadOfCreatingUnexpectedAuthorities() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", List.of("CUSTOMER", "ADMIN OR 1=1", "")))
                .claim("permissions", List.of("account:read", 42))
                .build();

        assertThat(names(converter.convert(jwt)))
                .containsExactlyInAnyOrder("ROLE_CUSTOMER", "account:read");
    }

    @Test
    void returnsNoAuthoritiesWhenClaimsAreMissingOrWrongType() {
        Jwt jwt = baseJwt()
                .claim("realm_access", Map.of("roles", "CUSTOMER"))
                .claim("resource_access", Map.of("banking-api", "not-a-map"))
                .build();

        assertThat(converter.convert(jwt)).isEmpty();
    }

    @Test
    void authenticationNameIsTheSubjectClaim() {
        Jwt jwt = baseJwt().claim("preferred_username", "alice").build();
        BankingJwtAuthenticationConvertersTestSupport support = new BankingJwtAuthenticationConvertersTestSupport();

        assertThat(support.authenticationName(jwt)).isEqualTo("8f14e45f-ceea-467a-9575-3c3a1c0b6f11");
    }

    private static Jwt.Builder baseJwt() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .subject("8f14e45f-ceea-467a-9575-3c3a1c0b6f11")
                .issuedAt(now)
                .expiresAt(now.plusSeconds(300));
    }

    private static Set<String> names(Collection<GrantedAuthority> authorities) {
        return authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
    }
}
