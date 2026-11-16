/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.service;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

/**
 * Generates non-sequential account numbers ({@code BNK} + 12 random digits), so numbers cannot be guessed
 * by incrementing a known one.
 *
 * @author Jyotiprakash Parida
 */
@Component
public class AccountNumberGenerator {

    private static final String PREFIX = "BNK";
    private static final int DIGITS = 12;

    private final SecureRandom random = new SecureRandom();

    public String next() {
        StringBuilder number = new StringBuilder(PREFIX.length() + DIGITS).append(PREFIX);
        for (int i = 0; i < DIGITS; i++) {
            number.append(random.nextInt(10));
        }
        return number.toString();
    }
}
