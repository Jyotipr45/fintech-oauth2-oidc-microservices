/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.config;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.CustomizableThreadFactory;

/**
 * Bounded thread pool (Java 17, no virtual threads) for asynchronous outbox delivery.
 *
 * <p>Fixed maximum threads and a bounded queue prevent unbounded memory growth under load. When the queue
 * is full, {@link ThreadPoolExecutor.CallerRunsPolicy} applies back-pressure by running the task on the
 * relay's own thread instead of dropping events.</p>
 *
 * @author Jyotiprakash Parida
 */
@Configuration(proxyBeanMethods = false)
public class ExecutorConfig {

    private static final long KEEP_ALIVE_SECONDS = 60L;

    @Bean(destroyMethod = "shutdown")
    public ThreadPoolExecutor outboxPublisherExecutor(TransferProperties properties) {
        TransferProperties.Executor settings = properties.executor();
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                settings.corePoolSize(),
                settings.maxPoolSize(),
                KEEP_ALIVE_SECONDS, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(settings.queueCapacity()),
                new CustomizableThreadFactory("outbox-publisher-"),
                new ThreadPoolExecutor.CallerRunsPolicy());
        executor.allowCoreThreadTimeOut(false);
        return executor;
    }
}
