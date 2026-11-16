/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for business-rule failures, mapped to RFC 7807 responses by the API exception handler.
 * Messages are safe to return to clients: they never contain balances, owners or internal identifiers.
 *
 * @author Jyotiprakash Parida
 */
public abstract class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    protected DomainException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
