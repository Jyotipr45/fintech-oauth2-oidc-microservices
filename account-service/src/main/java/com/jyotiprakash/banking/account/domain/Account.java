/*
 * Copyright (c) 2026 Jyotiprakash Parida
 * SPDX-License-Identifier: MIT
 */
package com.jyotiprakash.banking.account.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.jyotiprakash.banking.account.exception.AccountNotActiveException;
import com.jyotiprakash.banking.account.exception.InsufficientFundsException;

/**
 * Bank account aggregate. Never exposed over HTTP; controllers return DTOs.
 *
 * <p>{@code ownerSubject} is the Keycloak {@code sub} of the customer, set once from the verified JWT when
 * the account is opened and never updatable, so ownership cannot be reassigned through the API.</p>
 *
 * @author Jyotiprakash Parida
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "account_number", nullable = false, unique = true, updatable = false, length = 20)
    private String accountNumber;

    @Column(name = "owner_subject", nullable = false, updatable = false, length = 64)
    private String ownerSubject;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_type", nullable = false, updatable = false, length = 16)
    private AccountType type;

    @Column(name = "currency", nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(name = "balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private AccountStatus status;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Account() {
        // for JPA
    }

    private Account(String accountNumber, String ownerSubject, AccountType type, String currency) {
        this.id = UUID.randomUUID();
        this.accountNumber = accountNumber;
        this.ownerSubject = ownerSubject;
        this.type = type;
        this.currency = currency;
        this.balance = BigDecimal.ZERO.setScale(4);
        this.status = AccountStatus.ACTIVE;
    }

    public static Account open(String accountNumber, String ownerSubject, AccountType type, String currency) {
        return new Account(accountNumber, ownerSubject, type, currency);
    }

    public void debit(BigDecimal amount) {
        requireActive();
        if (balance.compareTo(amount) < 0) {
            throw new InsufficientFundsException();
        }
        balance = balance.subtract(amount);
    }

    public void credit(BigDecimal amount) {
        requireActive();
        balance = balance.add(amount);
    }

    public void changeStatus(AccountStatus newStatus) {
        this.status = newStatus;
    }

    public boolean isOwnedBy(String subject) {
        return ownerSubject.equals(subject);
    }

    private void requireActive() {
        if (status != AccountStatus.ACTIVE) {
            throw new AccountNotActiveException();
        }
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public String getAccountNumber() {
        return accountNumber;
    }

    public String getOwnerSubject() {
        return ownerSubject;
    }

    public AccountType getType() {
        return type;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
