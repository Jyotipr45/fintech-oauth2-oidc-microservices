/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Minimal view of an RFC 7807 error returned by account-service.
 *
 * @author Jyotiprakash Parida
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record RemoteProblem(Integer status, String errorCode) {
}
