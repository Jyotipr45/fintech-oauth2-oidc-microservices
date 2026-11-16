/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.exception;

import org.springframework.http.HttpStatus;

/**
 * Base class for business and integration failures mapped to RFC 7807 responses.
 *
 * @author Jyotiprakash Parida
 */
public class DomainException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    public DomainException(HttpStatus status, String errorCode, String message) {
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
