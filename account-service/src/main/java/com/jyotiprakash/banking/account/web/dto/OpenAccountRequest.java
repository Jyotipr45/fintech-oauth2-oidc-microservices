/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.web.dto;

import com.jyotiprakash.banking.account.domain.AccountType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request to open an account. There is intentionally no owner field: the owner is always the
 * authenticated caller's JWT {@code sub}.
 *
 * @author Jyotiprakash Parida
 */
public record OpenAccountRequest(
        @NotNull AccountType type,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$", message = "must be an ISO 4217 code such as INR") String currency) {
}
