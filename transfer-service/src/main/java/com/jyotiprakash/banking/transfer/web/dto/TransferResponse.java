/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.jyotiprakash.banking.transfer.domain.Transfer;
import com.jyotiprakash.banking.transfer.domain.TransferStatus;

/**
 * Public view of a transfer.
 *
 * @author Jyotiprakash Parida
 */
public record TransferResponse(
        UUID id,
        UUID sourceAccountId,
        String targetAccountNumber,
        BigDecimal amount,
        String currency,
        String reference,
        TransferStatus status,
        String failureCode,
        Instant createdAt) {

    public static TransferResponse from(Transfer transfer) {
        return new TransferResponse(transfer.getId(), transfer.getSourceAccountId(), transfer.getTargetAccountNumber(),
                transfer.getAmount(), transfer.getCurrency(), transfer.getReference(), transfer.getStatus(),
                transfer.getFailureCode(), transfer.getCreatedAt());
    }
}
