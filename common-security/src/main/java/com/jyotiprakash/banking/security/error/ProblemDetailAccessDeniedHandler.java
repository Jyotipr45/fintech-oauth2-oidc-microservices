/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.error;

import java.io.IOException;
import java.security.Principal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet {@link AccessDeniedHandler} returning {@code 403} as {@code application/problem+json}.
 * The denial is logged with the pseudonymous {@code sub} for audit trails; no token content is logged.
 *
 * @author Jyotiprakash Parida
 */
public final class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

    private final ProblemDetailSerializer serializer;

    public ProblemDetailAccessDeniedHandler(ProblemDetailSerializer serializer) {
        this.serializer = serializer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        Principal principal = request.getUserPrincipal();
        LOG.info("Access denied: subject={} method={} path={}",
                principal == null ? "anonymous" : principal.getName(), request.getMethod(), request.getRequestURI());

        if (response.isCommitted()) {
            return;
        }
        ProblemDetail problem = SecurityProblemDetails.forbidden(request.getRequestURI());
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, SecurityProblemDetails.forbiddenChallenge());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getOutputStream().write(serializer.toJsonBytes(problem));
    }
}
