/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.error;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.jyotiprakash.banking.security.logging.SensitiveDataMasker;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet {@link AuthenticationEntryPoint} returning {@code 401} as {@code application/problem+json}
 * with an RFC 6750 {@code WWW-Authenticate: Bearer ...} challenge.
 *
 * @author Jyotiprakash Parida
 */
public final class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailAuthenticationEntryPoint.class);

    private final ProblemDetailSerializer serializer;

    public ProblemDetailAuthenticationEntryPoint(ProblemDetailSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        LOG.debug("Authentication failed for {} {}: {}", request.getMethod(), request.getRequestURI(),
                SensitiveDataMasker.mask(authException.getMessage()));

        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problem = SecurityProblemDetails.unauthorized(authException, request.getRequestURI());
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, SecurityProblemDetails.unauthorizedChallenge(authException));
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(serializer.toJsonBytes(problem));
    }
}
