/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Transfer request. There is no "from user" field: the initiator is always the JWT {@code sub}.
 *
 * @author Jyotiprakash Parida
 */
public record CreateTransferRequest(
        @NotNull UUID sourceAccountId,
        @NotNull @Pattern(regexp = "^BNK[0-9]{12}$", message = "must be a valid account number") String targetAccountNumber,
        @NotNull @DecimalMin("0.01") @DecimalMax("200000.00") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @Size(max = 140) @Pattern(regexp = "^[\\p{L}\\p{N} .,:/\\-]*$") String reference) {
}
