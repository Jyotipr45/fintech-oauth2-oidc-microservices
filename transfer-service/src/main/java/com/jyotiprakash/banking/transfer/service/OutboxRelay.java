/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.jyotiprakash.banking.transfer.config.TransferProperties;
import com.jyotiprakash.banking.transfer.domain.OutboxEvent;
import com.jyotiprakash.banking.transfer.repository.OutboxEventRepository;

/**
 * Transactional-outbox relay: claims a batch of unpublished events with {@code FOR UPDATE SKIP LOCKED},
 * fans them out on the bounded {@link ThreadPoolExecutor}, and marks each one published only after
 * delivery succeeded (at-least-once). Failed events are retried on the next poll, up to a maximum number
 * of attempts.
 *
 * @author Jyotiprakash Parida
 */
@Component
public class OutboxRelay {

    private static final Logger LOG = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository outbox;
    private final EventPublisher publisher;
    private final ThreadPoolExecutor executor;
    private final TransactionTemplate transactions;
    private final TransferProperties.Outbox settings;

    public OutboxRelay(OutboxEventRepository outbox, EventPublisher publisher, ThreadPoolExecutor executor,
                       TransactionTemplate transactions, TransferProperties properties) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.executor = executor;
        this.transactions = transactions;
        this.settings = properties.outbox();
    }

    @Scheduled(fixedDelayString = "${transfer.outbox.poll-interval-ms:2000}")
    public void relay() {
        transactions.executeWithoutResult(status -> {
            List<OutboxEvent> batch = outbox.claimUnpublished(settings.batchSize(), settings.maxAttempts());
            if (batch.isEmpty()) {
                return;
            }
            List<CompletableFuture<Void>> deliveries = new ArrayList<>(batch.size());
            for (OutboxEvent event : batch) {
                deliveries.add(CompletableFuture.runAsync(() -> publisher.publish(event), executor));
            }
            for (int i = 0; i < batch.size(); i++) {
                OutboxEvent event = batch.get(i);
                if (awaitDelivery(deliveries.get(i))) {
                    event.markPublished();
                }
                else {
                    event.recordFailedAttempt();
                    LOG.warn("Outbox delivery failed: eventId={} attempts={}", event.getId(), event.getAttempts());
                }
            }
        });
    }

    private boolean awaitDelivery(CompletableFuture<Void> delivery) {
        try {
            delivery.get(settings.publishTimeout().toMillis(), TimeUnit.MILLISECONDS);
            return true;
        }
        catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        }
        catch (ExecutionException | TimeoutException ex) {
            return false;
        }
    }
}
