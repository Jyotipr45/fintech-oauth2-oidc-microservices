/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.gateway.config;

import java.net.InetSocketAddress;
import java.security.Principal;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.publisher.Mono;

/**
 * Key resolution for the Redis token-bucket {@code RequestRateLimiter}.
 *
 * <p>Buckets are keyed by the verified JWT {@code sub}, so a single user or machine client cannot exhaust
 * the platform by rotating source IPs, and users behind one corporate NAT do not starve each other.
 * Unauthenticated traffic (only health probes reach the gateway without a token) falls back to the peer
 * IP address.</p>
 *
 * @author Jyotiprakash Parida
 */
@Configuration(proxyBeanMethods = false)
public class RateLimitConfig {

    static final String SUBJECT_PREFIX = "sub:";
    static final String IP_PREFIX = "ip:";
    static final String UNKNOWN = "unknown";

    @Bean
    public KeyResolver subjectKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(Principal::getName)
                .map(subject -> SUBJECT_PREFIX + subject)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
                    String address = remote == null || remote.getAddress() == null
                            ? UNKNOWN
                            : remote.getAddress().getHostAddress();
                    return IP_PREFIX + address;
                }));
    }
}
