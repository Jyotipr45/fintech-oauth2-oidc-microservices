/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.web.dto;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Internal, service-to-service request to post a transfer to the ledger.
 *
 * <p>{@code initiatedBy} is the end user's {@code sub}, taken by transfer-service from the user's verified
 * token. account-service re-checks that this user owns the source account, so even a compromised
 * machine credential cannot move money out of an arbitrary account.</p>
 *
 * @author Jyotiprakash Parida
 */
public record LedgerTransferRequest(
        @NotNull UUID transferId,
        @NotNull UUID sourceAccountId,
        @NotBlank @Size(max = 20) String targetAccountNumber,
        @NotNull @DecimalMin(value = "0.01") @Digits(integer = 15, fraction = 2) BigDecimal amount,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency,
        @NotBlank @Size(max = 64) String initiatedBy) {
}
