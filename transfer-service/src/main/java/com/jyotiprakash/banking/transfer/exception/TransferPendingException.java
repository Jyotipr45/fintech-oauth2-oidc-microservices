/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when the ledger could not be reached; the transfer stays PENDING and is resumed by an idempotent retry.
 *
 * @author Jyotiprakash Parida
 */
public class TransferPendingException extends DomainException {

    public TransferPendingException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, "transfer_pending", "The transfer is pending. Retry with the same Idempotency-Key.");
    }
}
