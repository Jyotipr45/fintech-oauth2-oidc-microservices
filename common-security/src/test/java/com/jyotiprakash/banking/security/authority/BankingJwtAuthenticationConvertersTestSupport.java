/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.authority;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import com.jyotiprakash.banking.security.config.BankingSecurityProperties;

/**
 * Builds the production converter with default properties for tests.
 *
 * @author Jyotiprakash Parida
 */
final class BankingJwtAuthenticationConvertersTestSupport {

    String authenticationName(Jwt jwt) {
        BankingSecurityProperties properties = new BankingSecurityProperties();
        properties.setAudiences(List.of("account-service"));
        AbstractAuthenticationToken authentication = BankingJwtAuthenticationConverters.create(properties).convert(jwt);
        return authentication.getName();
    }
}
