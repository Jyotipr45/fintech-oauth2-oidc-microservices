/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.logging;

import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Correlation-id handling shared by the servlet and reactive access-log filters. Incoming ids are only
 * reused when they match a strict pattern, which prevents log injection through the header.
 *
 * @author Jyotiprakash Parida
 */
public final class CorrelationIds {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9\\-]{8,64}$");

    private CorrelationIds() {
    }

    public static String resolve(String incoming) {
        if (incoming != null && SAFE_ID.matcher(incoming).matches()) {
            return incoming;
        }
        return UUID.randomUUID().toString();
    }
}
