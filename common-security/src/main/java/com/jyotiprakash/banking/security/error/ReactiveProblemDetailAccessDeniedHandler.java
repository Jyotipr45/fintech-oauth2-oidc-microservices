/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.error;

import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.server.authorization.ServerAccessDeniedHandler;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Reactive (gateway) counterpart of {@link ProblemDetailAccessDeniedHandler}.
 *
 * @author Jyotiprakash Parida
 */
public final class ReactiveProblemDetailAccessDeniedHandler implements ServerAccessDeniedHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ReactiveProblemDetailAccessDeniedHandler.class);
    private static final String ANONYMOUS = "anonymous";

    private final ProblemDetailSerializer serializer;

    public ReactiveProblemDetailAccessDeniedHandler(ProblemDetailSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, AccessDeniedException denied) {
        String path = exchange.getRequest().getPath().value();
        return exchange.getPrincipal()
                .map(Principal::getName)
                .defaultIfEmpty(ANONYMOUS)
                .flatMap(subject -> {
                    LOG.info("Access denied: subject={} method={} path={}",
                            subject, exchange.getRequest().getMethod(), path);
                    return writeProblem(exchange.getResponse(), path);
                });
    }

    private Mono<Void> writeProblem(ServerHttpResponse response, String path) {
        if (response.isCommitted()) {
            return Mono.empty();
        }
        ProblemDetail problem = SecurityProblemDetails.forbidden(path);
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, SecurityProblemDetails.forbiddenChallenge());
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(serializer.toJsonBytes(problem));
        return response.writeWith(Mono.just(buffer));
    }
}
