/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.jyotiprakash.banking.transfer.domain.OutboxEvent;

/**
 * Default adapter that writes events to a dedicated audit log category. Stands in for a broker in the
 * local stack. Only ids and the event type are logged, never the payload.
 *
 * @author Jyotiprakash Parida
 */
@Component
public class LoggingEventPublisher implements EventPublisher {

    private static final Logger AUDIT = LoggerFactory.getLogger("banking.audit");

    @Override
    public void publish(OutboxEvent event) {
        AUDIT.info("event={} eventId={} transferId={}", event.getEventType(), event.getId(), event.getAggregateId());
    }
}
