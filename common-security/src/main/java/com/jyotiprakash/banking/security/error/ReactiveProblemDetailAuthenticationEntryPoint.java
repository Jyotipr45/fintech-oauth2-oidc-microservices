/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.web.server.ServerWebExchange;

import com.jyotiprakash.banking.security.logging.SensitiveDataMasker;

import reactor.core.publisher.Mono;

/**
 * Reactive (gateway) counterpart of {@link ProblemDetailAuthenticationEntryPoint}.
 *
 * @author Jyotiprakash Parida
 */
public final class ReactiveProblemDetailAuthenticationEntryPoint implements ServerAuthenticationEntryPoint {

    private static final Logger LOG = LoggerFactory.getLogger(ReactiveProblemDetailAuthenticationEntryPoint.class);

    private final ProblemDetailSerializer serializer;

    public ReactiveProblemDetailAuthenticationEntryPoint(ProblemDetailSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException authException) {
        String path = exchange.getRequest().getPath().value();
        LOG.debug("Authentication failed for {} {}: {}", exchange.getRequest().getMethod(), path,
                SensitiveDataMasker.mask(authException.getMessage()));

        ServerHttpResponse response = exchange.getResponse();
        if (response.isCommitted()) {
            return Mono.empty();
        }
        ProblemDetail problem = SecurityProblemDetails.unauthorized(authException, path);
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE,
                SecurityProblemDetails.unauthorizedChallenge(authException));
        response.getHeaders().setCacheControl("no-store");
        response.getHeaders().setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        DataBuffer buffer = response.bufferFactory().wrap(serializer.toJsonBytes(problem));
        return response.writeWith(Mono.just(buffer));
    }
}
