/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a transfer targets its own source account.
 *
 * @author Jyotiprakash Parida
 */
public class SameAccountTransferException extends DomainException {

    public SameAccountTransferException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "same_account", "Source and target accounts must differ.");
    }
}
