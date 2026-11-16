/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.service;

import com.jyotiprakash.banking.transfer.domain.OutboxEvent;

/**
 * Port for delivering outbox events to a message broker. Swap the implementation for Kafka/RabbitMQ
 * without touching the transfer logic (ports and adapters).
 *
 * @author Jyotiprakash Parida
 */
public interface EventPublisher {

    /**
     * Delivers one event. Must be idempotent on the consumer side: delivery is at-least-once.
     */
    void publish(OutboxEvent event);
}
