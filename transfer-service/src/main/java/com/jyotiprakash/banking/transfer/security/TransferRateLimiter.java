/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.security;

import java.time.Clock;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.jyotiprakash.banking.transfer.config.TransferProperties;

/**
 * Per-user fixed-window rate limit for transfer creation, stored in Redis so it holds across replicas.
 *
 * <p>This is a second, business-level limit behind the gateway's general token bucket: the gateway
 * protects capacity, while this limits how many money movements one identity can start per window
 * (velocity control against account takeover or scripted abuse). INCR and EXPIRE run atomically in one
 * Lua script.</p>
 *
 * @author Jyotiprakash Parida
 */
@Component
public class TransferRateLimiter {

    static final String KEY_PREFIX = "banking:transfer-rate:";

    private static final RedisScript<Long> INCREMENT_WITH_TTL = new DefaultRedisScript<>(
            "local current = redis.call('INCR', KEYS[1]) "
                    + "if current == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end "
                    + "return current",
            Long.class);

    private final StringRedisTemplate redis;
    private final TransferProperties.RateLimit settings;
    private final Clock clock;

    @Autowired
    public TransferRateLimiter(StringRedisTemplate redis, TransferProperties properties) {
        this(redis, properties.rateLimit(), Clock.systemUTC());
    }

    TransferRateLimiter(StringRedisTemplate redis, TransferProperties.RateLimit settings, Clock clock) {
        this.redis = redis;
        this.settings = settings;
        this.clock = clock;
    }

    public Decision tryAcquire(String subject) {
        long windowSeconds = Math.max(1L, settings.window().toSeconds());
        long nowSeconds = clock.instant().getEpochSecond();
        long windowIndex = nowSeconds / windowSeconds;
        String key = KEY_PREFIX + subject + ":" + windowIndex;

        Long count = redis.execute(INCREMENT_WITH_TTL, List.of(key), Long.toString(windowSeconds + 1));
        long used = count == null ? Long.MAX_VALUE : count;
        long retryAfter = windowSeconds - (nowSeconds % windowSeconds);
        return new Decision(used <= settings.maxRequests(), Math.max(0, settings.maxRequests() - used), retryAfter);
    }

    /**
     * Outcome of a rate-limit check.
     *
     * @param allowed           whether the request may proceed
     * @param remaining         requests left in the current window
     * @param retryAfterSeconds seconds until the window resets
     */
    public record Decision(boolean allowed, long remaining, long retryAfterSeconds) {
    }
}
