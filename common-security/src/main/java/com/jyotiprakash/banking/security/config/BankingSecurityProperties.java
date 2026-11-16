/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised security settings shared by every service, bound from {@code banking.security.*}.
 *
 * <p>Every value that differs between environments (issuer URL, audiences) is expected to come from
 * environment variables via {@code ${ENV_VAR}} placeholders in each service's {@code application.yml}.
 * Nothing in here is a secret: resource servers only ever need Keycloak's <em>public</em> keys.</p>
 *
 * @author Jyotiprakash Parida
 */
@ConfigurationProperties(prefix = "banking.security")
public class BankingSecurityProperties {

    /**
     * Expected {@code iss} claim and OIDC discovery location, e.g.
     * {@code http://keycloak:8080/realms/banking-realm}. Required.
     */
    private String issuerUri;

    /**
     * Optional explicit JWKS endpoint. When blank, the JWKS location is resolved from the issuer's
     * OIDC discovery document ({@code /.well-known/openid-configuration}). Set it only when the
     * services reach Keycloak through a different network address than the public issuer URL.
     */
    private String jwkSetUri;

    /**
     * Accepted {@code aud} values. A token is accepted when its audience contains at least one of these.
     * Required: an empty list would let a token minted for any other client be replayed here.
     */
    private List<String> audiences = new ArrayList<>();

    /**
     * Expected value of Keycloak's {@code typ} claim. Keycloak access tokens carry {@code Bearer};
     * ID tokens carry {@code ID} and refresh tokens {@code Refresh}, so this blocks token-type confusion.
     * Leave blank to disable the check.
     */
    private String requiredTokenType = "Bearer";

    /** Tolerated clock drift when checking {@code exp} and {@code nbf}. */
    private Duration clockSkew = Duration.ofSeconds(30);

    /**
     * How long the fetched JWKS document is cached before it is re-fetched. Keep this shorter than the
     * time a new signing key is published-but-passive in Keycloak during rotation (see docs).
     */
    private Duration jwksCacheTtl = Duration.ofMinutes(5);

    /** Keycloak client whose client roles ({@code resource_access.<client>.roles}) are fine-grained permissions. */
    private String permissionsClientId = "banking-api";

    /** Optional flat custom claim (string array) holding extra permissions, e.g. from a protocol mapper. */
    private String permissionsClaim = "permissions";

    /** Settings for the request-logging filter. */
    private final RequestLogging requestLogging = new RequestLogging();

    public String getIssuerUri() {
        return issuerUri;
    }

    public void setIssuerUri(String issuerUri) {
        this.issuerUri = issuerUri;
    }

    public String getJwkSetUri() {
        return jwkSetUri;
    }

    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    public List<String> getAudiences() {
        return audiences;
    }

    public void setAudiences(List<String> audiences) {
        this.audiences = audiences;
    }

    public String getRequiredTokenType() {
        return requiredTokenType;
    }

    public void setRequiredTokenType(String requiredTokenType) {
        this.requiredTokenType = requiredTokenType;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }

    public Duration getJwksCacheTtl() {
        return jwksCacheTtl;
    }

    public void setJwksCacheTtl(Duration jwksCacheTtl) {
        this.jwksCacheTtl = jwksCacheTtl;
    }

    public String getPermissionsClientId() {
        return permissionsClientId;
    }

    public void setPermissionsClientId(String permissionsClientId) {
        this.permissionsClientId = permissionsClientId;
    }

    public String getPermissionsClaim() {
        return permissionsClaim;
    }

    public void setPermissionsClaim(String permissionsClaim) {
        this.permissionsClaim = permissionsClaim;
    }

    public RequestLogging getRequestLogging() {
        return requestLogging;
    }

    /**
     * Request logging options. Headers are never logged; only method, masked path, status and timing.
     */
    public static class RequestLogging {

        /** Whether the masked access-log filter is registered. */
        private boolean enabled = true;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }
}
