/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when account-service, called with the user's own token, does not return the source account: it does not exist or the user does not own it.
 *
 * @author Jyotiprakash Parida
 */
public class SourceAccountNotFoundException extends DomainException {

    public SourceAccountNotFoundException() {
        super(HttpStatus.NOT_FOUND, "source_account_not_found", "Source account not found.");
    }
}
