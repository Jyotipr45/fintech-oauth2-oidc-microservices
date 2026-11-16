/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.logging;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;

/**
 * Logback converter ({@code %maskedEx}) that masks credentials inside exception messages and stack
 * traces, e.g. a token echoed back in a downstream HTTP client exception.
 *
 * @author Jyotiprakash Parida
 */
public class MaskingThrowableConverter extends ExtendedThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy throwableProxy) {
        return SensitiveDataMasker.mask(super.throwableProxyToString(throwableProxy));
    }
}
