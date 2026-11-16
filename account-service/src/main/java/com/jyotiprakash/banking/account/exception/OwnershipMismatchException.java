/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised by the internal ledger when the user on whose behalf a service acts does not own the source account.
 *
 * @author Jyotiprakash Parida
 */
public class OwnershipMismatchException extends DomainException {

    public OwnershipMismatchException() {
        super(HttpStatus.FORBIDDEN, "ownership_mismatch", "The initiating customer does not own the source account.");
    }
}
