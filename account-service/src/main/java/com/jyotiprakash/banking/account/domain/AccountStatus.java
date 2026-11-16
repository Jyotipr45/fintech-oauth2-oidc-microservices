/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.domain;

/**
 * Lifecycle state of an account. Only {@link #ACTIVE} accounts can move money.
 *
 * @author Jyotiprakash Parida
 */
public enum AccountStatus {
    ACTIVE,
    FROZEN,
    CLOSED
}
