/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.client;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The subset of account-service's account view that transfer-service needs. The balance is deliberately
 * not mapped: transfer-service never needs or stores it.
 *
 * @author Jyotiprakash Parida
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountSnapshot(UUID id, String accountNumber, String currency, String status) {

    public static final String ACTIVE = "ACTIVE";

    public boolean isActive() {
        return ACTIVE.equals(status);
    }
}
