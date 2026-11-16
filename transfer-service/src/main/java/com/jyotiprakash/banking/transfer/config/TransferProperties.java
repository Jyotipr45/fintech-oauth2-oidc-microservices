/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * transfer-service settings, bound from {@code transfer.*}.
 *
 * @param accountServiceUri    base URL of account-service (internal network)
 * @param clientRegistrationId Spring Security client registration used for Client Credentials
 * @param rateLimit            per-user limit on transfer creation (defense in depth behind the gateway)
 * @param outbox               transactional outbox relay settings
 * @param executor             bounded pool used by the outbox relay
 * @param http                 timeouts for calls to account-service
 * @author Jyotiprakash Parida
 */
@ConfigurationProperties(prefix = "transfer")
public record TransferProperties(
        String accountServiceUri,
        @DefaultValue("account-service") String clientRegistrationId,
        @DefaultValue RateLimit rateLimit,
        @DefaultValue Outbox outbox,
        @DefaultValue Executor executor,
        @DefaultValue Http http) {

    /**
     * Fixed-window limit per JWT subject for {@code POST /api/v1/transfers}.
     */
    public record RateLimit(@DefaultValue("5") int maxRequests, @DefaultValue("60s") Duration window) {
    }

    /**
     * Outbox relay settings.
     */
    public record Outbox(@DefaultValue("50") int batchSize, @DefaultValue("10") int maxAttempts,
                         @DefaultValue("5s") Duration publishTimeout) {
    }

    /**
     * Bounded {@link java.util.concurrent.ThreadPoolExecutor} sizing.
     */
    public record Executor(@DefaultValue("2") int corePoolSize, @DefaultValue("4") int maxPoolSize,
                           @DefaultValue("200") int queueCapacity) {
    }

    /**
     * HTTP client timeouts.
     */
    public record Http(@DefaultValue("2s") Duration connectTimeout, @DefaultValue("5s") Duration readTimeout) {
    }
}
