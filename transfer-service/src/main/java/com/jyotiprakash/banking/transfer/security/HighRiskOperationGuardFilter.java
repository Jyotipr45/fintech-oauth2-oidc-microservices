/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.security;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import com.jyotiprakash.banking.security.error.ProblemDetailSerializer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Extra checks for high-risk operations (creating a transfer), run right after the Bearer token has been
 * authenticated and before authorization:
 *
 * <ol>
 *   <li><b>Revocation</b> &mdash; the token must carry a {@code jti} that is not on the Redis denylist,
 *       otherwise {@code 401 invalid_token}.</li>
 *   <li><b>Velocity limit</b> &mdash; at most N transfer creations per user per window, otherwise
 *       {@code 429} with {@code Retry-After}.</li>
 * </ol>
 *
 * <p>Fail closed: if Redis is unavailable, money cannot move ({@code 503}), because "cannot verify
 * revocation" must never be treated as "not revoked".</p>
 *
 * @author Jyotiprakash Parida
 */
public class HighRiskOperationGuardFilter extends OncePerRequestFilter {

    static final String TRANSFERS_PATH = "/api/v1/transfers";

    private static final Logger LOG = LoggerFactory.getLogger(HighRiskOperationGuardFilter.class);

    private final TokenRevocationService revocations;
    private final TransferRateLimiter rateLimiter;
    private final AuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailSerializer serializer;

    public HighRiskOperationGuardFilter(TokenRevocationService revocations, TransferRateLimiter rateLimiter,
                                        AuthenticationEntryPoint authenticationEntryPoint,
                                        ProblemDetailSerializer serializer) {
        this.revocations = revocations;
        this.rateLimiter = rateLimiter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.serializer = serializer;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && TRANSFERS_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            // Not authenticated: let the authorization filter produce the 401.
            filterChain.doFilter(request, response);
            return;
        }
        Jwt jwt = jwtAuthentication.getToken();
        try {
            if (!StringUtils.hasText(jwt.getId())) {
                reject(request, response, new RevokedTokenException("The access token has no jti"));
                return;
            }
            if (revocations.isRevoked(jwt.getId())) {
                LOG.info("Rejected revoked token for subject={}", jwt.getSubject());
                reject(request, response, new RevokedTokenException("The access token has been revoked"));
                return;
            }
            TransferRateLimiter.Decision decision = rateLimiter.tryAcquire(jwt.getSubject());
            response.setHeader("X-Transfer-RateLimit-Remaining", Long.toString(decision.remaining()));
            if (!decision.allowed()) {
                LOG.info("Transfer velocity limit hit for subject={}", jwt.getSubject());
                response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(decision.retryAfterSeconds()));
                writeProblem(response, request, HttpStatus.TOO_MANY_REQUESTS, "transfer_rate_limited",
                        "Too many transfers in a short period. Retry later.");
                return;
            }
        }
        catch (DataAccessException ex) {
            LOG.error("Redis unavailable for high-risk checks; failing closed: {}", ex.getClass().getSimpleName());
            writeProblem(response, request, HttpStatus.SERVICE_UNAVAILABLE, "security_check_unavailable",
                    "Security checks are temporarily unavailable. Retry later.");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void reject(HttpServletRequest request, HttpServletResponse response, RevokedTokenException ex)
            throws IOException, ServletException {
        SecurityContextHolder.clearContext();
        authenticationEntryPoint.commence(request, response, ex);
    }

    private void writeProblem(HttpServletResponse response, HttpServletRequest request, HttpStatus status,
                              String errorCode, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("urn:problem-type:banking:" + errorCode));
        problem.setTitle(status.getReasonPhrase());
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("errorCode", errorCode);
        problem.setProperty("timestamp", Instant.now().toString());
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.getOutputStream().write(serializer.toJsonBytes(problem));
    }
}
