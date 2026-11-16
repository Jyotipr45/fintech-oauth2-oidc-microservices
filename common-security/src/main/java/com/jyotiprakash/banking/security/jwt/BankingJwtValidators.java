/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.jwt;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

import com.jyotiprakash.banking.security.config.BankingSecurityProperties;

/**
 * Builds the complete claim-validation chain applied <em>after</em> the RS256 signature has been verified.
 *
 * <ul>
 *   <li>{@code exp} &mdash; must be present</li>
 *   <li>{@code exp} / {@code nbf} &mdash; {@link JwtTimestampValidator} with a bounded clock skew</li>
 *   <li>{@code iss} &mdash; exact match against the configured Keycloak realm issuer</li>
 *   <li>{@code aud} &mdash; must contain one of this service's audiences</li>
 *   <li>{@code sub} &mdash; must be present (it is the identity used for ownership checks)</li>
 *   <li>{@code typ} &mdash; must be {@code Bearer}, so ID or refresh tokens cannot be used as access tokens</li>
 * </ul>
 *
 * @author Jyotiprakash Parida
 */
public final class BankingJwtValidators {

    static final String KEYCLOAK_TOKEN_TYPE_CLAIM = "typ";

    private BankingJwtValidators() {
    }

    public static OAuth2TokenValidator<Jwt> create(BankingSecurityProperties properties) {
        Assert.hasText(properties.getIssuerUri(), "banking.security.issuer-uri must be configured");
        Assert.notEmpty(properties.getAudiences(), "banking.security.audiences must contain at least one value");

        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        // JwtTimestampValidator only checks exp/nbf when present, so require exp explicitly:
        // a token without an expiry would otherwise be valid forever.
        validators.add(new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull));
        validators.add(new JwtTimestampValidator(properties.getClockSkew()));
        validators.add(new JwtIssuerValidator(properties.getIssuerUri()));
        validators.add(new AudienceValidator(properties.getAudiences()));
        validators.add(new JwtClaimValidator<String>(JwtClaimNames.SUB, StringUtils::hasText));

        String requiredType = properties.getRequiredTokenType();
        if (StringUtils.hasText(requiredType)) {
            validators.add(new JwtClaimValidator<String>(KEYCLOAK_TOKEN_TYPE_CLAIM, requiredType::equalsIgnoreCase));
        }
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
