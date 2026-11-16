/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.logging;

import java.util.regex.Pattern;

/**
 * Redacts credentials from free text before it reaches a log sink.
 *
 * <p>Masks, in this order:</p>
 * <ol>
 *   <li>{@code Bearer} / {@code Basic} credentials, e.g. a copied {@code Authorization} header</li>
 *   <li>key/value secrets in JSON, form or query syntax ({@code access_token=...}, {@code "password":"..."})</li>
 *   <li>any remaining compact JWS/JWT ({@code eyJ...}.{@code ...}.{@code ...})</li>
 * </ol>
 *
 * <p>This is a safety net, not a licence to log tokens: code should never pass headers or tokens to a
 * logger in the first place.</p>
 *
 * @author Jyotiprakash Parida
 */
public final class SensitiveDataMasker {

    public static final String REDACTED = "[REDACTED]";

    private static final Pattern AUTH_SCHEME_CREDENTIALS =
            Pattern.compile("(?i)\\b(bearer|basic)\\s+[A-Za-z0-9\\-._~+/]+=*");

    private static final Pattern SECRET_KEY_VALUE = Pattern.compile(
            "(?i)(\"?\\b(?:access_token|refresh_token|id_token|client_secret|password|passwd|secret"
                    + "|code_verifier|authorization)\\b\"?\\s*[:=]\\s*\"?)([^\"&,;\\s}]+)");

    private static final Pattern COMPACT_JWT =
            Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*");

    private SensitiveDataMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String masked = AUTH_SCHEME_CREDENTIALS.matcher(text).replaceAll("$1 " + REDACTED);
        masked = SECRET_KEY_VALUE.matcher(masked).replaceAll("$1" + REDACTED);
        masked = COMPACT_JWT.matcher(masked).replaceAll(REDACTED);
        return masked;
    }
}
