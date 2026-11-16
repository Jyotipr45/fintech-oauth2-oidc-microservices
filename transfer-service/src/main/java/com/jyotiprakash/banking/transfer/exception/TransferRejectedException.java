/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.exception;

import org.springframework.http.HttpStatus;

/**
 * account-service refused the posting for a business reason (insufficient funds, frozen account, ...).
 * The error code from account-service is preserved; its free-text detail is not forwarded.
 *
 * @author Jyotiprakash Parida
 */
public class TransferRejectedException extends DomainException {

    public TransferRejectedException(HttpStatus status, String errorCode) {
        super(status, errorCode, "The transfer was rejected: " + errorCode);
    }
}
