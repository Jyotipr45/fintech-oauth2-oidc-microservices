/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.transfer.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.jyotiprakash.banking.transfer.domain.Transfer;

/**
 * Transfer persistence; customer reads are always scoped by owner subject.
 *
 * @author Jyotiprakash Parida
 */
public interface TransferRepository extends JpaRepository<Transfer, UUID> {

    Optional<Transfer> findByOwnerSubjectAndIdempotencyKey(String ownerSubject, String idempotencyKey);

    Optional<Transfer> findByIdAndOwnerSubject(UUID id, String ownerSubject);

    Page<Transfer> findAllByOwnerSubject(String ownerSubject, Pageable pageable);
}
