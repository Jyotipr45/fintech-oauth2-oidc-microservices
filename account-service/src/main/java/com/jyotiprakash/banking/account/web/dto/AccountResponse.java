/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.jyotiprakash.banking.account.domain.Account;
import com.jyotiprakash.banking.account.domain.AccountStatus;
import com.jyotiprakash.banking.account.domain.AccountType;

/**
 * Public view of an account. The owner's identity is deliberately not included.
 *
 * @author Jyotiprakash Parida
 */
public record AccountResponse(
        UUID id,
        String accountNumber,
        AccountType type,
        String currency,
        BigDecimal balance,
        AccountStatus status,
        Instant createdAt) {

    public static AccountResponse from(Account account) {
        return new AccountResponse(account.getId(), account.getAccountNumber(), account.getType(),
                account.getCurrency(), account.getBalance(), account.getStatus(), account.getCreatedAt());
    }
}
