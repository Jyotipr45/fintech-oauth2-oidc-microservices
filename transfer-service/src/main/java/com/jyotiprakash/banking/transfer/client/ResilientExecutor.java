/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.client;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.jyotiprakash.banking.transfer.exception.DownstreamUnavailableException;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadFullException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;

/**
 * Applies Retry(CircuitBreaker(Bulkhead(call))) to calls to account-service.
 *
 * <p>Retry is outermost so that each attempt is counted by the circuit breaker; the bulkhead is innermost
 * so that it limits real concurrent network calls. An open circuit or a full bulkhead fails fast and is
 * reported as {@link DownstreamUnavailableException}.</p>
 *
 * @author Jyotiprakash Parida
 */
@Component
public class ResilientExecutor {

    private final CircuitBreaker circuitBreaker;
    private final Retry retry;
    private final Bulkhead bulkhead;

    public ResilientExecutor(CircuitBreaker accountServiceCircuitBreaker, Retry accountServiceRetry,
                             Bulkhead accountServiceBulkhead) {
        this.circuitBreaker = accountServiceCircuitBreaker;
        this.retry = accountServiceRetry;
        this.bulkhead = accountServiceBulkhead;
    }

    public <T> T execute(Supplier<T> call) {
        Supplier<T> decorated = Retry.decorateSupplier(retry,
                CircuitBreaker.decorateSupplier(circuitBreaker,
                        Bulkhead.decorateSupplier(bulkhead, call)));
        try {
            return decorated.get();
        }
        catch (CallNotPermittedException ex) {
            throw new DownstreamUnavailableException("account-service circuit is open");
        }
        catch (BulkheadFullException ex) {
            throw new DownstreamUnavailableException("account-service concurrency limit reached");
        }
    }

    public CircuitBreaker.State circuitState() {
        return circuitBreaker.getState();
    }
}
