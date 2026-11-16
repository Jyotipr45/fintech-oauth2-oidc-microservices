/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis-backed denylist of revoked access-token ids ({@code jti}).
 *
 * <p>JWTs are self-contained and stay valid until {@code exp}. For high-risk operations the platform also
 * checks this denylist, so a token can be killed immediately (logout, suspected theft). Each entry expires
 * together with the token it blocks, so the denylist never grows without bound.</p>
 *
 * @author Jyotiprakash Parida
 */
@Service
public class TokenRevocationService {

    static final String KEY_PREFIX = "banking:revoked-jti:";
    private static final Duration SAFETY_MARGIN = Duration.ofSeconds(60);

    private final StringRedisTemplate redis;
    private final Clock clock;

    @Autowired
    public TokenRevocationService(StringRedisTemplate redis) {
        this(redis, Clock.systemUTC());
    }

    TokenRevocationService(StringRedisTemplate redis, Clock clock) {
        this.redis = redis;
        this.clock = clock;
    }

    public boolean isRevoked(String jti) {
        return Boolean.TRUE.equals(redis.hasKey(KEY_PREFIX + jti));
    }

    /**
     * @return {@code true} if an entry was written, {@code false} if the token had already expired
     */
    public boolean revoke(String jti, Instant expiresAt) {
        Duration ttl = Duration.between(clock.instant(), expiresAt).plus(SAFETY_MARGIN);
        if (ttl.isNegative() || ttl.isZero()) {
            return false;
        }
        redis.opsForValue().set(KEY_PREFIX + jti, "1", ttl);
        return true;
    }
}
