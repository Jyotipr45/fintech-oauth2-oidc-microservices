/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a transfer's currency does not match both accounts.
 *
 * @author Jyotiprakash Parida
 */
public class CurrencyMismatchException extends DomainException {

    public CurrencyMismatchException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "currency_mismatch", "Source and target accounts must use the transfer currency.");
    }
}
