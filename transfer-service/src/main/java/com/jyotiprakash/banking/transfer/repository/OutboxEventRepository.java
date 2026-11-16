/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jyotiprakash.banking.transfer.domain.OutboxEvent;

/**
 * Outbox persistence.
 *
 * @author Jyotiprakash Parida
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Claims the next batch of unpublished events. {@code FOR UPDATE SKIP LOCKED} lets several service
     * replicas relay in parallel without publishing the same event twice and without blocking each other.
     */
    @Query(value = "SELECT * FROM transfer_outbox WHERE published_at IS NULL AND attempts < :maxAttempts "
            + "ORDER BY created_at LIMIT :batchSize FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> claimUnpublished(@Param("batchSize") int batchSize, @Param("maxAttempts") int maxAttempts);
}
