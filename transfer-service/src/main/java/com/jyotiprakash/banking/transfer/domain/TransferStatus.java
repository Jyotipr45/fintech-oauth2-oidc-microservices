/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.domain;

/**
 * Transfer lifecycle (a small orchestration saga):
 * {@code PENDING} until the ledger posting succeeds ({@code COMPLETED}) or is rejected ({@code FAILED}).
 * A {@code PENDING} transfer is safely resumed by retrying with the same {@code Idempotency-Key}.
 *
 * @author Jyotiprakash Parida
 */
public enum TransferStatus {
    PENDING,
    COMPLETED,
    FAILED
}
