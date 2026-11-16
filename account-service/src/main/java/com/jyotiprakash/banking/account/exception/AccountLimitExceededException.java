/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a customer tries to open more accounts than allowed.
 *
 * @author Jyotiprakash Parida
 */
public class AccountLimitExceededException extends DomainException {

    public AccountLimitExceededException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "account_limit_exceeded", "The maximum number of accounts per customer has been reached.");
    }
}
