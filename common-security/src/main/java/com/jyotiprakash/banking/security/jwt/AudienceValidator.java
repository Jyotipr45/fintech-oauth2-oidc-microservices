/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.jwt;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.Assert;

/**
 * Validates the {@code aud} claim: the token must have been minted for (at least) one of the audiences
 * this service accepts. Without this check a valid token issued by the same realm for an unrelated
 * client could be replayed against this API (the "confused deputy" problem).
 *
 * @author Jyotiprakash Parida
 */
public final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID_AUDIENCE = new OAuth2Error(
            OAuth2ErrorCodes.INVALID_TOKEN, "The token audience is not accepted by this service", null);

    private final Set<String> acceptedAudiences;

    public AudienceValidator(Collection<String> acceptedAudiences) {
        Assert.notEmpty(acceptedAudiences, "At least one accepted audience must be configured");
        this.acceptedAudiences = Set.copyOf(acceptedAudiences);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        List<String> tokenAudiences = jwt.getAudience();
        if (tokenAudiences != null) {
            for (String audience : tokenAudiences) {
                if (acceptedAudiences.contains(audience)) {
                    return OAuth2TokenValidatorResult.success();
                }
            }
        }
        return OAuth2TokenValidatorResult.failure(INVALID_AUDIENCE);
    }
}
