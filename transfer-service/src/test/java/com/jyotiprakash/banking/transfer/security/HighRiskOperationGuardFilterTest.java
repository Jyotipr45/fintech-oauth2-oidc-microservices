/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jyotiprakash.banking.security.error.ProblemDetailAuthenticationEntryPoint;
import com.jyotiprakash.banking.security.error.ProblemDetailSerializer;

/**
 * jti revocation, velocity limit and fail-closed behaviour for high-risk requests.
 *
 * @author Jyotiprakash Parida
 */
class HighRiskOperationGuardFilterTest {

    private final TokenRevocationService revocations = mock(TokenRevocationService.class);
    private final TransferRateLimiter rateLimiter = mock(TransferRateLimiter.class);
    private final ProblemDetailSerializer serializer = new ProblemDetailSerializer(new ObjectMapper());
    private final HighRiskOperationGuardFilter filter = new HighRiskOperationGuardFilter(
            revocations, rateLimiter, new ProblemDetailAuthenticationEntryPoint(serializer), serializer);

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void revokedTokenIsRejectedWith401() throws Exception {
        authenticate("jti-123");
        when(revocations.isRevoked("jti-123")).thenReturn(true);

        MockHttpServletResponse response = run();

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getHeader("WWW-Authenticate")).contains("invalid_token");
    }

    @Test
    void tokenWithoutJtiIsRejectedWith401() throws Exception {
        authenticate(null);
        assertThat(run().getStatus()).isEqualTo(401);
    }

    @Test
    void velocityLimitReturns429WithRetryAfter() throws Exception {
        authenticate("jti-1");
        when(rateLimiter.tryAcquire(anyString())).thenReturn(new TransferRateLimiter.Decision(false, 0, 42));

        MockHttpServletResponse response = run();

        assertThat(response.getStatus()).isEqualTo(429);
        assertThat(response.getHeader("Retry-After")).isEqualTo("42");
        assertThat(response.getContentAsString(StandardCharsets.UTF_8)).contains("transfer_rate_limited");
    }

    @Test
    void failsClosedWhenRedisIsDown() throws Exception {
        authenticate("jti-1");
        when(revocations.isRevoked("jti-1")).thenThrow(new RedisConnectionFailureException("down"));

        assertThat(run().getStatus()).isEqualTo(503);
    }

    @Test
    void validTokenPassesThrough() throws Exception {
        authenticate("jti-1");
        when(rateLimiter.tryAcquire(anyString())).thenReturn(new TransferRateLimiter.Decision(true, 4, 60));
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(transferRequest(), new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void readOnlyRequestsAreNotGuarded() throws Exception {
        MockHttpServletRequest get = new MockHttpServletRequest("GET", "/api/v1/transfers");
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(get, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    private MockHttpServletResponse run() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(transferRequest(), response, new MockFilterChain());
        return response;
    }

    private static MockHttpServletRequest transferRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/transfers");
        request.setRequestURI("/api/v1/transfers");
        return request;
    }

    private static void authenticate(String jti) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("alice")
                .audience(List.of("transfer-service"))
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300));
        if (jti != null) {
            builder.jti(jti);
        }
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(builder.build()));
    }
}
