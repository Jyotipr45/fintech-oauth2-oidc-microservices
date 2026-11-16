/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.logging;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet access log that records method, path, status and latency with a correlation id.
 *
 * <p>Request headers (including {@code Authorization}) and query strings are never logged, because
 * either may carry credentials; the path is still passed through {@link SensitiveDataMasker}.</p>
 *
 * @author Jyotiprakash Parida
 */
public class MaskedAccessLogFilter extends OncePerRequestFilter {

    private static final Logger ACCESS_LOG = LoggerFactory.getLogger("banking.access");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String correlationId = CorrelationIds.resolve(request.getHeader(CorrelationIds.HEADER));
        long startNanos = System.nanoTime();
        MDC.put(CorrelationIds.MDC_KEY, correlationId);
        response.setHeader(CorrelationIds.HEADER, correlationId);
        try {
            filterChain.doFilter(request, response);
        }
        finally {
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            ACCESS_LOG.info("{} {} -> {} ({} ms)", request.getMethod(),
                    SensitiveDataMasker.mask(request.getRequestURI()), response.getStatus(), elapsedMillis);
            MDC.remove(CorrelationIds.MDC_KEY);
        }
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
