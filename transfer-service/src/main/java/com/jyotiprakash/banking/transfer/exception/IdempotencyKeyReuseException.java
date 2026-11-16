/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.exception;

import org.springframework.http.HttpStatus;

/**
 * Raised when an Idempotency-Key is replayed with a different payload.
 *
 * @author Jyotiprakash Parida
 */
public class IdempotencyKeyReuseException extends DomainException {

    public IdempotencyKeyReuseException() {
        super(HttpStatus.UNPROCESSABLE_ENTITY, "idempotency_key_reuse", "This Idempotency-Key was already used for a different transfer.");
    }
}
