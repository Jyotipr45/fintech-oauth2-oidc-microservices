/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.jyotiprakash.banking.account.domain.Account;

import jakarta.persistence.LockModeType;

/**
 * Account persistence. Customer-facing reads use the {@code ...AndOwnerSubject} variants so the ownership
 * predicate is part of the SQL itself, not an after-the-fact check in Java.
 *
 * @author Jyotiprakash Parida
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

    Optional<Account> findByIdAndOwnerSubject(UUID id, String ownerSubject);

    List<Account> findAllByOwnerSubjectOrderByCreatedAtAsc(String ownerSubject);

    long countByOwnerSubject(String ownerSubject);

    boolean existsByAccountNumber(String accountNumber);

    @Query("select a.id from Account a where a.accountNumber = :accountNumber")
    Optional<UUID> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Account a where a.id = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);
}
