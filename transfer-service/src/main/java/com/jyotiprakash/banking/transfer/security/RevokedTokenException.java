/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.security;

import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;

/**
 * The access token is cryptographically valid but has been revoked (its {@code jti} is on the denylist)
 * or lacks a {@code jti}. Rendered as {@code 401 invalid_token}.
 *
 * @author Jyotiprakash Parida
 */
public class RevokedTokenException extends OAuth2AuthenticationException {

    public RevokedTokenException(String description) {
        super(new OAuth2Error(BearerTokenErrorCodes.INVALID_TOKEN, description, null), description);
    }
}
