/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.gateway.web;

import java.net.URI;
import java.time.Instant;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Circuit-breaker fallback. Reached only through the gateway's internal {@code forward:} (the security
 * chain denies direct calls to this path). Answers fast with a 503 problem and {@code Retry-After}
 * instead of letting the client wait for a dead service.
 *
 * @author Jyotiprakash Parida
 */
@RestController
@RequestMapping("/fallback")
public class FallbackController {

    static final String RETRY_AFTER_SECONDS = "30";

    @RequestMapping("/account-service")
    public ResponseEntity<ProblemDetail> accountServiceFallback() {
        return unavailable("account-service");
    }

    @RequestMapping("/transfer-service")
    public ResponseEntity<ProblemDetail> transferServiceFallback() {
        return unavailable("transfer-service");
    }

    static ResponseEntity<ProblemDetail> unavailable(String service) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                "The " + service + " is temporarily unavailable. Retry later.");
        problem.setType(URI.create("urn:problem-type:banking:service_unavailable"));
        problem.setTitle("Service Unavailable");
        problem.setProperty("errorCode", "service_unavailable");
        problem.setProperty("timestamp", Instant.now().toString());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .contentType(MediaType.APPLICATION_PROBLEM_JSON)
                .body(problem);
    }
}
