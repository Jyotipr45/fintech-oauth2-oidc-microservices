/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.client;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Ledger posting result: {@code APPLIED} or {@code ALREADY_APPLIED} (idempotent retry).
 *
 * @author Jyotiprakash Parida
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LedgerPostingResponse(UUID transferId, String outcome) {
}
