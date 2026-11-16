/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.error;

import java.nio.charset.StandardCharsets;

import org.springframework.http.ProblemDetail;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Serialises {@link ProblemDetail} responses for the security error handlers, which run inside the
 * filter chain where Spring MVC / WebFlux message converters are not available.
 *
 * @author Jyotiprakash Parida
 */
public final class ProblemDetailSerializer {

    private static final byte[] FALLBACK_BODY =
            "{\"title\":\"Error\",\"status\":500}".getBytes(StandardCharsets.UTF_8);

    private final ObjectMapper objectMapper;

    public ProblemDetailSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public byte[] toJsonBytes(ProblemDetail problem) {
        try {
            return objectMapper.writeValueAsBytes(SecurityProblemDetails.toBody(problem));
        }
        catch (JsonProcessingException ex) {
            return FALLBACK_BODY;
        }
    }
}
