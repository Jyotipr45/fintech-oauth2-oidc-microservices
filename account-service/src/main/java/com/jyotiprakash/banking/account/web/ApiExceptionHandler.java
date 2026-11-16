/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.web;

import java.net.URI;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.jyotiprakash.banking.account.exception.DomainException;

import jakarta.validation.ConstraintViolationException;

/**
 * Maps exceptions to RFC 7807 problem details. Unexpected errors return a generic 500 without internal
 * detail; security exceptions are re-thrown so Spring Security's 401/403 handlers produce the response.
 *
 * @author Jyotiprakash Parida
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    private static final String ERROR_CODE = "errorCode";
    private static final String TIMESTAMP = "timestamp";

    @ExceptionHandler(DomainException.class)
    public ProblemDetail handleDomain(DomainException ex) {
        return problem(ex.getStatus(), ex.getErrorCode(), ex.getMessage());
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    public ProblemDetail handleConflict(RuntimeException ex) {
        LOG.info("Concurrent modification or constraint violation: {}", ex.getClass().getSimpleName());
        return problem(HttpStatus.CONFLICT, "conflict", "The resource was modified concurrently. Retry the request.");
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation_failed", "Request parameters are invalid.");
    }

    /**
     * Let Spring Security's ExceptionTranslationFilter render 401/403 with the shared problem handlers.
     */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    public void rethrowSecurityException(RuntimeException ex) {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        LOG.error("Unhandled exception", ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "An unexpected error occurred.");
    }

    static ProblemDetail problem(HttpStatus status, String errorCode, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem-type:banking:" + errorCode));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty(ERROR_CODE, errorCode);
        problem.setProperty(TIMESTAMP, Instant.now().toString());
        return problem;
    }
}
