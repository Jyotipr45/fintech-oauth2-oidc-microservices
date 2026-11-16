/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.jyotiprakash.banking.transfer.domain.Transfer;
import com.jyotiprakash.banking.transfer.domain.TransferStatus;

/**
 * Integration event stored in the outbox. Contains no balances and no personal data.
 *
 * @author Jyotiprakash Parida
 */
public record TransferEvent(UUID transferId, TransferStatus status, BigDecimal amount, String currency,
                            String failureCode, Instant occurredAt) {

    public static TransferEvent of(Transfer transfer) {
        return new TransferEvent(transfer.getId(), transfer.getStatus(), transfer.getAmount(), transfer.getCurrency(),
                transfer.getFailureCode(), Instant.now());
    }
}
