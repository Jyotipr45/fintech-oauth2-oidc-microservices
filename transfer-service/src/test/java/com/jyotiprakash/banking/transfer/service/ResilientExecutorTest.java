/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.jyotiprakash.banking.transfer.client.ResilientExecutor;
import com.jyotiprakash.banking.transfer.exception.DownstreamUnavailableException;
import com.jyotiprakash.banking.transfer.exception.TransferRejectedException;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

/**
 * Retry / circuit-breaker semantics for calls to account-service.
 *
 * @author Jyotiprakash Parida
 */
class ResilientExecutorTest {

    private final CircuitBreaker breaker = CircuitBreaker.of("test", CircuitBreakerConfig.custom()
            .slidingWindowSize(4).minimumNumberOfCalls(4).failureRateThreshold(50.0f)
            .waitDurationInOpenState(Duration.ofMinutes(1))
            .recordException(DownstreamUnavailableException.class::isInstance)
            .build());
    private final Retry retry = Retry.of("test", RetryConfig.custom().maxAttempts(3).waitDuration(Duration.ofMillis(1))
            .retryOnException(DownstreamUnavailableException.class::isInstance).build());
    private final ResilientExecutor executor = new ResilientExecutor(breaker, retry, Bulkhead.ofDefaults("test"));

    @Test
    void transientFailureIsRetried() {
        AtomicInteger attempts = new AtomicInteger();
        String result = executor.execute(() -> {
            if (attempts.incrementAndGet() < 3) {
                throw new DownstreamUnavailableException("blip");
            }
            return "ok";
        });
        assertThat(result).isEqualTo("ok");
        assertThat(attempts).hasValue(3);
    }

    @Test
    void businessRejectionIsNeitherRetriedNorCounted() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> executor.execute(() -> {
            attempts.incrementAndGet();
            throw new TransferRejectedException(HttpStatus.UNPROCESSABLE_ENTITY, "x");
        })).isInstanceOf(TransferRejectedException.class);
        assertThat(attempts).hasValue(1);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void circuitOpensAndThenFailsFastWithoutCallingDownstream() {
        AtomicInteger calls = new AtomicInteger();
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> executor.execute(() -> {
                calls.incrementAndGet();
                throw new DownstreamUnavailableException("down");
            })).isInstanceOf(DownstreamUnavailableException.class);
        }
        assertThat(executor.circuitState()).isEqualTo(CircuitBreaker.State.OPEN);

        int before = calls.get();
        assertThatThrownBy(() -> executor.execute(() -> {
            calls.incrementAndGet();
            return "never";
        })).isInstanceOf(DownstreamUnavailableException.class).hasMessageContaining("circuit is open");
        assertThat(calls).hasValue(before);
    }
}
