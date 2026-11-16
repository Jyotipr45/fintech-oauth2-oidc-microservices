/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised for missing transfers and for transfers owned by someone else (no enumeration).
 *
 * @author Jyotiprakash Parida
 */
public class TransferNotFoundException extends DomainException {

    public TransferNotFoundException() {
        super(HttpStatus.NOT_FOUND, "transfer_not_found", "Transfer not found.");
    }
}
