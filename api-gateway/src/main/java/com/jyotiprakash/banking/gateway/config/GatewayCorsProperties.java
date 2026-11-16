/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.gateway.config;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * CORS settings for browser clients, bound from {@code gateway.cors.*}.
 * Origins must be listed explicitly; wildcards are rejected at startup.
 *
 * @author Jyotiprakash Parida
 */
@ConfigurationProperties(prefix = "gateway.cors")
public class GatewayCorsProperties {

    /** Exact origins (scheme + host + port) allowed to call the API from a browser. */
    private List<String> allowedOrigins = new ArrayList<>();

    /** How long browsers may cache a preflight response. */
    private Duration maxAge = Duration.ofHours(1);

    public List<String> getAllowedOrigins() {
        return allowedOrigins;
    }

    public void setAllowedOrigins(List<String> allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    public Duration getMaxAge() {
        return maxAge;
    }

    public void setMaxAge(Duration maxAge) {
        this.maxAge = maxAge;
    }
}
