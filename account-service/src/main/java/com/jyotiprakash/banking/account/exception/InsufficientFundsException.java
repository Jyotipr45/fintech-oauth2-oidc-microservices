/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when a debit would make the balance negative.
 *
 * @author Jyotiprakash Parida
 */
public class InsufficientFundsException extends DomainException {

    public InsufficientFundsException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "insufficient_funds", "The source account has insufficient funds for this operation.");
    }
}
