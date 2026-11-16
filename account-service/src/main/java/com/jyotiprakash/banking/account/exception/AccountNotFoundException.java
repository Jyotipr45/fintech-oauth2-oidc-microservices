/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised both when an account does not exist and when it belongs to someone else, so that callers cannot probe which account ids exist (BOLA/IDOR enumeration).
 *
 * @author Jyotiprakash Parida
 */
public class AccountNotFoundException extends DomainException {

    public AccountNotFoundException() {
        super(HttpStatus.NOT_FOUND, "account_not_found", "Account not found.");
    }
}
