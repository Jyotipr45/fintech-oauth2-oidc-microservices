/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.jyotiprakash.banking.account.domain.LedgerEntry;

/**
 * Ledger persistence.
 *
 * @author Jyotiprakash Parida
 */
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByTransferId(UUID transferId);
}
