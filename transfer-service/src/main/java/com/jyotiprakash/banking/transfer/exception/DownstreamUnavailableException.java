/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.exception;

import org.springframework.http.HttpStatus;

/**
 * A dependency failed in a retryable way (timeout, connection error, 5xx, open circuit, full bulkhead).
 * Only this exception is retried and counted by the circuit breaker; business rejections are not.
 *
 * @author Jyotiprakash Parida
 */
public class DownstreamUnavailableException extends DomainException {

    public DownstreamUnavailableException(String message) {
        super(HttpStatus.SERVICE_UNAVAILABLE, "dependency_unavailable", message);
    }
}
