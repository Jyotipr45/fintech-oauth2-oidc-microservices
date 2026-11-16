/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.web.dto;

import com.jyotiprakash.banking.account.domain.AccountStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Administrative status change (freeze, unfreeze, close) with a mandatory audit reason.
 *
 * @author Jyotiprakash Parida
 */
public record UpdateAccountStatusRequest(
        @NotNull AccountStatus status,
        @NotBlank @Size(max = 200) String reason) {
}
