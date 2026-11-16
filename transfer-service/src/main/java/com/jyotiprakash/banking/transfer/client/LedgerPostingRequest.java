/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.client;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Body of account-service's internal ledger endpoint. {@code initiatedBy} is the end user's verified
 * {@code sub}, which account-service re-checks against the source account owner.
 *
 * @author Jyotiprakash Parida
 */
public record LedgerPostingRequest(
        UUID transferId,
        UUID sourceAccountId,
        String targetAccountNumber,
        BigDecimal amount,
        String currency,
        String initiatedBy) {
}
