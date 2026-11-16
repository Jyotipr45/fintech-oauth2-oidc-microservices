/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.authority;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

/**
 * Maps Keycloak's nested claim layout to Spring Security authorities.
 *
 * <pre>
 * {
 *   "realm_access":    { "roles": ["CUSTOMER"] },                  -&gt; ROLE_CUSTOMER
 *   "resource_access": { "banking-api": { "roles": ["account:read"] } } -&gt; account:read
 *   "permissions":     ["transfer:create"],                         -&gt; transfer:create
 *   "scope":           "openid roles"                               -&gt; SCOPE_openid, SCOPE_roles
 * }
 * </pre>
 *
 * <p>Realm roles become coarse {@code ROLE_*} authorities for {@code hasRole(..)}; client roles of the
 * configured resource client and the optional flat {@code permissions} claim become fine-grained
 * permission authorities for {@code hasAuthority(..)}. Values that do not match a conservative
 * character set, and claims of an unexpected JSON shape, are ignored so a malformed claim can never be
 * turned into an unexpected authority or an exception.</p>
 *
 * @author Jyotiprakash Parida
 */
public final class KeycloakGrantedAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String ROLE_PREFIX = "ROLE_";
    public static final String SCOPE_PREFIX = "SCOPE_";

    static final String REALM_ACCESS_CLAIM = "realm_access";
    static final String RESOURCE_ACCESS_CLAIM = "resource_access";
    static final String ROLES_KEY = "roles";
    static final String SCOPE_CLAIM = "scope";

    private static final Pattern SAFE_AUTHORITY_VALUE = Pattern.compile("^[A-Za-z0-9_.:\\-]{1,64}$");

    private final String permissionsClientId;
    private final String permissionsClaim;

    public KeycloakGrantedAuthoritiesConverter(String permissionsClientId, String permissionsClaim) {
        this.permissionsClientId = permissionsClientId;
        this.permissionsClaim = permissionsClaim;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();

        Map<String, Object> claims = jwt.getClaims();

        for (String role : rolesOf(claims.get(REALM_ACCESS_CLAIM))) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.toUpperCase(Locale.ROOT)));
        }

        if (StringUtils.hasText(permissionsClientId)) {
            if (claims.get(RESOURCE_ACCESS_CLAIM) instanceof Map<?, ?> resourceAccess
                    && resourceAccess.get(permissionsClientId) instanceof Map<?, ?> client) {
                for (String permission : stringValues(client.get(ROLES_KEY))) {
                    authorities.add(new SimpleGrantedAuthority(permission));
                }
            }
        }

        if (StringUtils.hasText(permissionsClaim)) {
            for (String permission : stringValues(claims.get(permissionsClaim))) {
                authorities.add(new SimpleGrantedAuthority(permission));
            }
        }

        if (claims.get(SCOPE_CLAIM) instanceof String scope && StringUtils.hasText(scope)) {
            for (String value : scope.trim().split("\\s+")) {
                if (isSafe(value)) {
                    authorities.add(new SimpleGrantedAuthority(SCOPE_PREFIX + value));
                }
            }
        }
        return Collections.unmodifiableSet(authorities);
    }

    private static Set<String> rolesOf(Object container) {
        if (container instanceof Map<?, ?> map) {
            return stringValues(map.get(ROLES_KEY));
        }
        return Collections.emptySet();
    }

    private static Set<String> stringValues(Object raw) {
        if (!(raw instanceof Collection<?> values)) {
            return Collections.emptySet();
        }
        Set<String> result = new LinkedHashSet<>();
        for (Object value : values) {
            if (value instanceof String text && isSafe(text)) {
                result.add(text);
            }
        }
        return result;
    }

    private static boolean isSafe(String value) {
        return SAFE_AUTHORITY_VALUE.matcher(value).matches();
    }
}
