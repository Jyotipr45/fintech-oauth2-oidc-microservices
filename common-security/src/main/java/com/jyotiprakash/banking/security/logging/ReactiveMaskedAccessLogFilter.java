/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Reactive access log for the gateway. Resolves a correlation id, forwards it to downstream services
 * in {@code X-Correlation-Id}, echoes it on the response, and logs method, masked path, status and
 * latency. Headers and query strings are never logged.
 *
 * @author Jyotiprakash Parida
 */
public class ReactiveMaskedAccessLogFilter implements WebFilter, Ordered {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("banking.access");
    private static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 10;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = CorrelationIds.resolve(
                exchange.getRequest().getHeaders().getFirst(CorrelationIds.HEADER));
        long startNanos = System.nanoTime();

        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(CorrelationIds.HEADER, correlationId))
                .build();
        ServerWebExchange mutated = exchange.mutate().request(request).build();
        mutated.getResponse().getHeaders().set(CorrelationIds.HEADER, correlationId);

        return chain.filter(mutated).doFinally(signal -> {
            HttpStatusCode status = mutated.getResponse().getStatusCode();
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            ACCESS_LOG.info("[{}] {} {} -> {} ({} ms)", correlationId, request.getMethod(),
                    SensitiveDataMasker.mask(request.getPath().value()),
                    status == null ? "-" : Integer.toString(status.value()), elapsedMillis);
        });
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
