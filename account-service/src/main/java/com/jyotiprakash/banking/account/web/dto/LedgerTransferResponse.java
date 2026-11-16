/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.web.dto;

import java.util.UUID;

/**
 * Result of a ledger posting. {@code ALREADY_APPLIED} means a retry of a transfer that was already posted.
 *
 * @author Jyotiprakash Parida
 */
public record LedgerTransferResponse(UUID transferId, Outcome outcome) {

    /**
     * Posting outcome.
     */
    public enum Outcome {
        APPLIED,
        ALREADY_APPLIED
    }
}
