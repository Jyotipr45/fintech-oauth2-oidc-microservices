/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.jyotiprakash.banking.transfer.exception.DownstreamUnavailableException;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Resilience policies for calls to account-service:
 *
 * <ul>
 *   <li><b>Bulkhead</b> &mdash; at most 20 concurrent calls, so a slow dependency cannot exhaust the
 *       request threads.</li>
 *   <li><b>Circuit breaker</b> &mdash; opens when 50% of the last 20 calls fail, then fails fast for
 *       30 s instead of piling up timeouts.</li>
 *   <li><b>Retry</b> &mdash; up to 3 attempts with exponential back-off and jitter. Safe because reads are
 *       idempotent and ledger postings are deduplicated by transfer id.</li>
 * </ul>
 *
 * <p>Only {@link DownstreamUnavailableException} (timeouts, I/O errors, 5xx) is retried or counted as a
 * failure; business rejections (4xx) are neither.</p>
 *
 * @author Jyotiprakash Parida
 */
@Configuration(proxyBeanMethods = false)
public class ResilienceConfig {

    static final String ACCOUNT_SERVICE = "account-service";

    @Bean
    public CircuitBreaker accountServiceCircuitBreaker() {
        return CircuitBreaker.of(ACCOUNT_SERVICE, CircuitBreakerConfig.custom()
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .failureRateThreshold(50.0f)
                .slowCallDurationThreshold(Duration.ofSeconds(3))
                .slowCallRateThreshold(80.0f)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .permittedNumberOfCallsInHalfOpenState(3)
                .recordException(DownstreamUnavailableException.class::isInstance)
                .build());
    }

    @Bean
    public Retry accountServiceRetry() {
        return Retry.of(ACCOUNT_SERVICE, RetryConfig.custom()
                .maxAttempts(3)
                .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(Duration.ofMillis(200), 2.0d))
                .retryOnException(DownstreamUnavailableException.class::isInstance)
                .build());
    }

    @Bean
    public Bulkhead accountServiceBulkhead() {
        return Bulkhead.of(ACCOUNT_SERVICE, BulkheadConfig.custom()
                .maxConcurrentCalls(20)
                .maxWaitDuration(Duration.ofMillis(200))
                .build());
    }
}
