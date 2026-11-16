/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.security.error;

import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.server.resource.BearerTokenErrorCodes;

/**
 * Builds RFC 7807 problem documents and RFC 6750 {@code WWW-Authenticate} challenges for 401/403.
 *
 * <p>Messages are deliberately generic: they never echo the token, never reveal which validation step
 * failed in detail (expired vs bad signature vs wrong audience), and never include stack traces. The
 * specific reason is logged server-side, with tokens masked.</p>
 *
 * @author Jyotiprakash Parida
 */
public final class SecurityProblemDetails {

    public static final String REALM = "banking";
    public static final URI UNAUTHORIZED_TYPE = URI.create("urn:problem-type:banking:unauthorized");
    public static final URI FORBIDDEN_TYPE = URI.create("urn:problem-type:banking:forbidden");

    static final String ERROR_CODE_PROPERTY = "errorCode";
    static final String TIMESTAMP_PROPERTY = "timestamp";

    private SecurityProblemDetails() {
    }

    public static ProblemDetail unauthorized(AuthenticationException exception, String path) {
        String errorCode = bearerErrorCode(exception);
        String detail = BearerTokenErrorCodes.INVALID_REQUEST.equals(errorCode)
                ? "The request is missing a well-formed Bearer access token."
                : hasBearerError(exception)
                        ? "The access token is invalid, expired, or was not issued for this service."
                        : "Authentication with a Bearer access token is required.";

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, detail);
        problem.setType(UNAUTHORIZED_TYPE);
        problem.setTitle("Unauthorized");
        problem.setInstance(safeInstance(path));
        problem.setProperty(ERROR_CODE_PROPERTY, errorCode == null ? "unauthenticated" : errorCode);
        problem.setProperty(TIMESTAMP_PROPERTY, Instant.now().toString());
        return problem;
    }

    public static ProblemDetail forbidden(String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
                "The authenticated principal is not allowed to perform this operation.");
        problem.setType(FORBIDDEN_TYPE);
        problem.setTitle("Forbidden");
        problem.setInstance(safeInstance(path));
        problem.setProperty(ERROR_CODE_PROPERTY, BearerTokenErrorCodes.INSUFFICIENT_SCOPE);
        problem.setProperty(TIMESTAMP_PROPERTY, Instant.now().toString());
        return problem;
    }

    /**
     * RFC 6750 section 3 challenge. Without an error code the client simply has not authenticated.
     */
    public static String unauthorizedChallenge(AuthenticationException exception) {
        String errorCode = bearerErrorCode(exception);
        if (errorCode == null) {
            return "Bearer realm=\"" + REALM + "\"";
        }
        return "Bearer realm=\"" + REALM + "\", error=\"" + errorCode + "\"";
    }

    public static String forbiddenChallenge() {
        return "Bearer realm=\"" + REALM + "\", error=\"" + BearerTokenErrorCodes.INSUFFICIENT_SCOPE + "\"";
    }

    /**
     * Flattens a {@link ProblemDetail} into a JSON-ready map, independent of Jackson mix-in registration.
     */
    public static Map<String, Object> toBody(ProblemDetail problem) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", problem.getType().toString());
        body.put("title", problem.getTitle());
        body.put("status", problem.getStatus());
        body.put("detail", problem.getDetail());
        if (problem.getInstance() != null) {
            body.put("instance", problem.getInstance().toString());
        }
        if (problem.getProperties() != null) {
            body.putAll(problem.getProperties());
        }
        return body;
    }

    private static boolean hasBearerError(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException;
    }

    private static String bearerErrorCode(AuthenticationException exception) {
        if (exception instanceof OAuth2AuthenticationException oauth2Exception) {
            OAuth2Error error = oauth2Exception.getError();
            if (BearerTokenErrorCodes.INVALID_REQUEST.equals(error.getErrorCode())) {
                return BearerTokenErrorCodes.INVALID_REQUEST;
            }
            return BearerTokenErrorCodes.INVALID_TOKEN;
        }
        return null;
    }

    private static URI safeInstance(String path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        try {
            return URI.create(path);
        }
        catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
