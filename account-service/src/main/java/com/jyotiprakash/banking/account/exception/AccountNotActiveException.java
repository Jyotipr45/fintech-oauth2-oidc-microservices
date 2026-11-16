/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when money movement is attempted on a frozen or closed account.
 *
 * @author Jyotiprakash Parida
 */
public class AccountNotActiveException extends DomainException {

    public AccountNotActiveException() {
        super(HttpStatus.CONFLICT, "account_not_active", "The account is not active.");
    }
}
